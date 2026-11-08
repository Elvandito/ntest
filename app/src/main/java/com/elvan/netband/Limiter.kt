package com.elvan.netband

/**
 * Port of netband.py's Limiter class: HTB (tc) rate limiting + iptables DROP blocking.
 * Root-only (needs tc/iptables binaries and CAP_NET_ADMIN).
 */
object Limiter {
    private var tcBin = "tc"
    private var iptBin = "iptables"
    private var idCounter = 10
    private var ownsQdisc = false

    // mac -> (uploadClassId, downloadClassId)
    private val classIds = mutableMapOf<String, Pair<Int, Int>>()

    fun resolveBinaries() {
        tcBin = findBin("tc")
        iptBin = findBin("iptables")
    }

    private fun findBin(name: String): String {
        val candidates = listOf(
            "/system/bin/$name", "/system/xbin/$name", "/vendor/bin/$name",
            "/data/adb/magisk/$name", "/data/adb/modules/busybox-ndk/system/xbin/$name"
        )
        for (c in candidates) if (RootShell.execBool("[ -x $c ]")) return c
        return name // fall back to PATH lookup
    }

    private fun nextId(): Int {
        idCounter++
        return idCounter
    }

    private fun ensureRoot(iface: String) {
        val show = RootShell.exec("$tcBin qdisc show dev $iface").joinToString()
        if (!show.contains("htb")) {
            if (RootShell.execBool("$tcBin qdisc replace dev $iface root handle 1: htb default 1")) {
                if (RootShell.execBool("$tcBin class add dev $iface parent 1: classid 1:1 htb rate 1000gbit ceil 1000gbit")) {
                    ownsQdisc = true
                } else {
                    RootShell.execBool("$tcBin qdisc del dev $iface root")
                }
            }
        }
    }

    fun limit(iface: String, device: Device, rate: String, direction: Direction): Boolean {
        ensureRoot(iface)
        if (device.limitRate != null || device.blocked) unlimit(iface, device)

        val uid = nextId()
        val did = nextId()
        var ok = true

        if (direction == Direction.UP || direction == Direction.BOTH) {
            ok = ok && RootShell.execBool("$tcBin class add dev $iface parent 1: classid 1:$uid htb rate $rate ceil $rate burst 64k cburst 64k")
            ok = ok && RootShell.execBool("$tcBin filter add dev $iface parent 1: protocol ip prio $uid handle $uid fw flowid 1:$uid")
            ok = ok && RootShell.execBool("$iptBin -t mangle -A POSTROUTING -s ${device.ip} -j MARK --set-mark $uid")
        }
        if (direction == Direction.DOWN || direction == Direction.BOTH) {
            ok = ok && RootShell.execBool("$tcBin class add dev $iface parent 1: classid 1:$did htb rate $rate ceil $rate burst 64k cburst 64k")
            ok = ok && RootShell.execBool("$tcBin filter add dev $iface parent 1: protocol ip prio $did handle $did fw flowid 1:$did")
            ok = ok && RootShell.execBool("$iptBin -t mangle -A PREROUTING -d ${device.ip} -j MARK --set-mark $did")
        }

        if (ok) {
            device.limitRate = rate
            device.direction = direction
            device.blocked = false
            classIds[device.mac] = uid to did
        }
        return ok
    }

    fun block(iface: String, device: Device, direction: Direction): Boolean {
        if (device.limitRate != null || device.blocked) unlimit(iface, device)
        var ok = true

        if (direction == Direction.UP || direction == Direction.BOTH) {
            ok = ok && RootShell.execBool("$iptBin -I FORWARD -s ${device.ip} -j DROP")
            ok = ok && RootShell.execBool("$iptBin -I OUTPUT -s ${device.ip} -j DROP")
        }
        if (direction == Direction.DOWN || direction == Direction.BOTH) {
            ok = ok && RootShell.execBool("$iptBin -I FORWARD -d ${device.ip} -j DROP")
            ok = ok && RootShell.execBool("$iptBin -I INPUT -d ${device.ip} -j DROP")
        }

        if (ok) {
            device.blocked = true
            device.direction = direction
            device.limitRate = null
        }
        return ok
    }

    fun unlimit(iface: String, device: Device) {
        val direction = device.direction
        if (device.limitRate != null) {
            val (uid, did) = classIds[device.mac] ?: (0 to 0)
            if (direction == Direction.UP || direction == Direction.BOTH) {
                RootShell.execBool("$tcBin filter del dev $iface parent 1: prio $uid")
                RootShell.execBool("$tcBin class del dev $iface parent 1: classid 1:$uid")
                RootShell.execBool("$iptBin -t mangle -D POSTROUTING -s ${device.ip} -j MARK --set-mark $uid")
            }
            if (direction == Direction.DOWN || direction == Direction.BOTH) {
                RootShell.execBool("$tcBin filter del dev $iface parent 1: prio $did")
                RootShell.execBool("$tcBin class del dev $iface parent 1: classid 1:$did")
                RootShell.execBool("$iptBin -t mangle -D PREROUTING -d ${device.ip} -j MARK --set-mark $did")
            }
            classIds.remove(device.mac)
        } else if (device.blocked) {
            if (direction == Direction.UP || direction == Direction.BOTH) {
                RootShell.execBool("$iptBin -D FORWARD -s ${device.ip} -j DROP")
                RootShell.execBool("$iptBin -D OUTPUT -s ${device.ip} -j DROP")
            }
            if (direction == Direction.DOWN || direction == Direction.BOTH) {
                RootShell.execBool("$iptBin -D FORWARD -d ${device.ip} -j DROP")
                RootShell.execBool("$iptBin -D INPUT -d ${device.ip} -j DROP")
            }
        }
        device.limitRate = null
        device.blocked = false
        maybeRemoveRoot(iface)
    }

    private fun maybeRemoveRoot(iface: String) {
        if (ownsQdisc && classIds.isEmpty()) {
            if (RootShell.execBool("$tcBin qdisc del dev $iface root")) ownsQdisc = false
        }
    }

    fun flush(iface: String, devices: Collection<Device>) {
        devices.forEach { if (it.limitRate != null || it.blocked) unlimit(iface, it) }
        if (ownsQdisc) {
            RootShell.execBool("$tcBin qdisc del dev $iface root")
            ownsQdisc = false
        }
    }
}
