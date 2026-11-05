package com.elvan.netband

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import kotlinx.coroutines.delay
import java.net.UnknownHostException

data class UiState(
    val hasRoot: Boolean = false,
    val netInfo: NetInfo? = null,
    val gatewayMac: String = "",
    val devices: List<Device> = emptyList(),
    val scanning: Boolean = false,
    val message: String = "",
    val scanRange: String = "",
    val monitor: InterfaceStats.Counters? = null,
    val previousMonitor: InterfaceStats.Counters? = null,
    val rxRateBytesPerSecond: Double? = null,
    val txRateBytesPerSecond: Double? = null,
    val analysisResult: String? = null,
    val analysisTargetId: Int? = null,
    val analyzing: Boolean = false,
    val watchIntervalMs: Long = 5_000,
    val watchWindowSeconds: Long = 300,
    val watchEvents: List<String> = emptyList()
)

class NetBandViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var deviceMap: MutableMap<String, Device> = mutableMapOf()
    private var spoofer: ArpSpoofer? = null
    private val prefs = app.getSharedPreferences("netband_watch", Application.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val hasRoot = RootShell.isRootAvailable()
            val ni = NetworkInfo.detect(getApplication())
            deviceMap = DeviceStore.load(getApplication())
            _state.value = _state.value.copy(
                hasRoot = hasRoot,
                netInfo = ni,
                devices = sortedDevices(),
                watchIntervalMs = prefs.getLong("interval_ms", 5_000),
                watchWindowSeconds = prefs.getLong("window_seconds", 300),
                message = if (ni == null) "No active wired or Wi-Fi network detected" else ""
            )
            if (hasRoot) Limiter.resolveBinaries()
            if (ni != null) resolveGateway(ni)
            if (ni != null) refreshMonitor()
        }
    }

    private fun resolveGateway(ni: NetInfo) {
        val arp = Scanner.readArpTable(ni.iface)
        val mac = arp[ni.gatewayIp] ?: ""
        _state.value = _state.value.copy(gatewayMac = mac)
        if (mac.isNotEmpty() && spoofer == null) {
            spoofer = ArpSpoofer(
                getApplication(), ni.iface,
                ni.localIp, NetworkInfo.localMac(ni.iface),
                ni.gatewayIp, mac
            )
        }
    }

    fun setScanRange(value: String) {
        _state.value = _state.value.copy(scanRange = value)
    }

    fun scan() = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        _state.value = _state.value.copy(scanning = true, message = "Scanning network…")

        val allIps = Scanner.buildHostList(ni.localIp, ni.prefixLength)
        if (allIps.isEmpty()) {
            _state.value = _state.value.copy(scanning = false, message = "Subnet too large or invalid; scan skipped")
            return@launch
        }
        val ips = rangeFilter(allIps, _state.value.scanRange)
        if (ips == null) {
            _state.value = _state.value.copy(scanning = false, message = "Invalid range; use e.g. 192.168.1.10-192.168.1.80")
            return@launch
        }
        if (ips.isEmpty()) {
            _state.value = _state.value.copy(scanning = false, message = "Range contains no addresses")
            return@launch
        }

        val before = deviceMap.values.associateBy { it.mac }
        Scanner.pingSweep(ips)
        val neigh = Scanner.readArpTable(ni.iface)
        val newEvents = mutableListOf<String>()

        var nextId = (deviceMap.values.maxOfOrNull { it.id } ?: 0) + 1
        for ((ip, mac) in neigh) {
            if (ip == ni.localIp || ip == ni.gatewayIp) continue
            val existing = deviceMap[mac]
            if (existing != null) {
                if (existing.watched && existing.ip != ip) {
                    newEvents.add("${existing.id} changed ${existing.ip} → $ip")
                }
                existing.ip = ip
                existing.online = true
            } else {
                deviceMap[mac] = Device(id = nextId++, ip = ip, mac = mac, online = true)
            }
        }

        val seenMacs = neigh.values.toSet()
        deviceMap.values.forEach { d ->
            if (d.mac.startsWith("manual-") || d.mac.startsWith("manual:")) {
                return@forEach
            }
            if (d.mac !in seenMacs && d.limitRate == null && !d.blocked) d.online = false
        }

        DeviceStore.save(getApplication(), deviceMap)
        if (newEvents.isNotEmpty()) {
            _state.value = _state.value.copy(watchEvents = (_state.value.watchEvents + newEvents).takeLast(24))
        }
        resolveGateway(ni)
        refreshMonitor()
        _state.value = _state.value.copy(
            scanning = false,
            devices = sortedDevices(),
            message = "Found ${neigh.size} host${if (neigh.size == 1) "" else "s"}"
        )
    }

    private fun rangeFilter(ips: List<String>, rawRange: String): List<String>? {
        val range = rawRange.trim()
        if (range.isEmpty()) return ips
        val parts = range.split('-').map { it.trim() }
        if (parts.size != 2) return null
        val start = ipToInt(parts[0]) ?: return null
        val end = ipToInt(parts[1]) ?: return null
        if (end < start) return null
        val valid = ips.mapNotNull { ip -> ipToInt(ip)?.let { ip to it } }.filter { it.second in start..end }.map { it.first }
        return valid
    }

    private fun ipToInt(ip: String): Int? = try {
        val address = InetAddress.getByName(ip)
        val bytes = address.address
        if (bytes.size != 4) null else ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
    } catch (_: UnknownHostException) {
        null
    }

    fun addHost(ip: String, mac: String = "") = viewModelScope.launch(Dispatchers.IO) {
        val trimmedIp = ip.trim()
        if (ipToInt(trimmedIp) == null) {
            setMessage("Invalid IPv4 address")
            return@launch
        }
        val normalizedMac = mac.trim().lowercase()
        if (normalizedMac.isNotEmpty() && !normalizedMac.matches(Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))) {
            setMessage("Invalid MAC; use aa:bb:cc:dd:ee:ff")
            return@launch
        }
        val existing = deviceMap.values.firstOrNull { it.ip == trimmedIp }
        val hostnames = try {
            val names = InetAddress.getByName(trimmedIp).getCanonicalHostName()
            if (names == trimmedIp) "" else names
        } catch (_: Exception) {
            ""
        }
        if (existing != null) {
            existing.online = true
            if (hostnames.isNotEmpty()) existing.hostname = hostnames
            if (normalizedMac.isNotEmpty()) {
                deviceMap.remove(existing.mac)
                existing.mac = normalizedMac
                deviceMap[normalizedMac] = existing
            }
            DeviceStore.save(getApplication(), deviceMap)
            refreshDevices()
            setMessage("Updated $trimmedIp")
            return@launch
        }
        val arpMac = Scanner.readArpTable(_state.value.netInfo?.iface ?: return@launch)[trimmedIp]
        val key = normalizedMac.ifEmpty { arpMac ?: "manual:$trimmedIp" }
        val nextId = (deviceMap.values.maxOfOrNull { it.id } ?: 0) + 1
        deviceMap[key] = Device(
            id = nextId,
            ip = trimmedIp,
            mac = key,
            hostname = hostnames,
            online = true
        )
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage("Added $trimmedIp")
    }

    fun limit(device: Device, rate: String, direction: Direction) = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        if (!_state.value.hasRoot) { setMessage("Root required to apply a bandwidth limit"); return@launch }
        if (!rate.matches(Regex("^[0-9]+[0-9]*(kbit|mbit|gbit|bit)$"))) {
            setMessage("Invalid rate; e.g. 512kbit, 2mbit, 10mbit")
            return@launch
        }
        val ok = Limiter.limit(ni.iface, device, rate, direction)
        if (ok) spoofer?.addTarget(device.ip, device.mac)
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage(if (ok) "Limited ${device.ip} → $rate" else "Could not limit ${device.ip}")
    }

    fun block(device: Device, direction: Direction) = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        if (!_state.value.hasRoot) { setMessage("Root required to block a host"); return@launch }
        val ok = Limiter.block(ni.iface, device, direction)
        if (ok) spoofer?.addTarget(device.ip, device.mac)
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage(if (ok) "Blocked ${device.ip}" else "Could not block ${device.ip}")
    }

    fun free(device: Device) = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        Limiter.unlimit(ni.iface, device)
        spoofer?.removeTarget(device.ip)
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage("Freed ${device.ip}")
    }

    fun freeAll() = viewModelScope.launch(Dispatchers.IO) {
        val ni = _state.value.netInfo ?: return@launch
        val affected = deviceMap.values.filter { it.limitRate != null || it.blocked }
        affected.forEach { Limiter.unlimit(ni.iface, it); spoofer?.removeTarget(it.ip) }
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage(if (affected.isEmpty()) "Nothing to free" else "Freed ${affected.size} host${if (affected.size == 1) "" else "s"}")
    }

    fun toggleWatch(device: Device) = viewModelScope.launch(Dispatchers.IO) {
        device.watched = !device.watched
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage(if (device.watched) "Watching ${device.ip}" else "Stopped watching ${device.ip}")
    }

    fun watchAll() = viewModelScope.launch(Dispatchers.IO) {
        deviceMap.values.forEach { it.watched = true }
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage("All hosts are watched")
    }

    fun unwatchAll() = viewModelScope.launch(Dispatchers.IO) {
        deviceMap.values.forEach { it.watched = false }
        DeviceStore.save(getApplication(), deviceMap)
        refreshDevices()
        setMessage("All hosts removed from watch")
    }

    fun setWatchSettings(intervalMs: Long, windowSeconds: Long) {
        val nextInterval = intervalMs.coerceIn(500L, 60_000L)
        val nextWindow = windowSeconds.coerceIn(30L, 86_400L)
        prefs.edit().putLong("interval_ms", nextInterval).putLong("window_seconds", nextWindow).apply()
        _state.value = _state.value.copy(watchIntervalMs = nextInterval, watchWindowSeconds = nextWindow)
        setMessage("Watch updated: interval ${nextInterval} ms · window ${nextWindow} s")
    }

    fun clear() = viewModelScope.launch(Dispatchers.IO) {
        deviceMap.clear()
        DeviceStore.save(getApplication(), deviceMap)
        _state.value = _state.value.copy(devices = emptyList(), message = "Host list and watch entries cleared", watchEvents = emptyList())
    }

    fun refreshMonitor() {
        val iface = _state.value.netInfo?.iface ?: return
        val current = InterfaceStats.read(iface, _state.value.hasRoot) ?: return
        val previous = _state.value.monitor
        val elapsedSeconds = previous?.let { (current.sampledAtMs - it.sampledAtMs).coerceAtLeast(1) / 1000.0 }
        val rxRate = if (previous != null && elapsedSeconds != null) (current.rxBytes - previous.rxBytes) / elapsedSeconds else null
        val txRate = if (previous != null && elapsedSeconds != null) (current.txBytes - previous.txBytes) / elapsedSeconds else null
        _state.value = _state.value.copy(
            previousMonitor = previous,
            monitor = current,
            rxRateBytesPerSecond = rxRate?.coerceAtLeast(0.0),
            txRateBytesPerSecond = txRate?.coerceAtLeast(0.0)
        )
    }

    fun analyze(device: Device, durationSeconds: Int) = viewModelScope.launch(Dispatchers.IO) {
        val iface = _state.value.netInfo?.iface
        if (iface == null) return@launch
        val seconds = durationSeconds.coerceIn(1, 120)
        _state.value = _state.value.copy(analyzing = true, analysisResult = null, analysisTargetId = device.id)
        val before = InterfaceStats.read(iface, _state.value.hasRoot)
        delay(seconds * 1000L)
        val after = InterfaceStats.read(iface, _state.value.hasRoot)
        val body = if (before == null || after == null) {
            "Network counters for ${device.ip} were not readable from /proc/net/dev on this device."
        } else {
            val rx = (after.rxBytes - before.rxBytes).coerceAtLeast(0)
            val tx = (after.txBytes - before.txBytes).coerceAtLeast(0)
            val rxPackets = (after.rxPackets - before.rxPackets).coerceAtLeast(0)
            val txPackets = (after.txPackets - before.txPackets).coerceAtLeast(0)
            val down = InterfaceStats.formatBytes(rx)
            val up = InterfaceStats.formatBytes(tx)
            val rxRate = InterfaceStats.formatRate(rx / seconds.toDouble())
            val txRate = InterfaceStats.formatRate(tx / seconds.toDouble())
            val status = when {
                device.blocked -> "Blocked (${device.direction})"
                device.limitRate != null -> "Limited ${device.limitRate} (${device.direction})"
                device.online -> "Online"
                else -> "Offline"
            }
            "ANALYSIS · ${device.ip}\n" +
                "Host: ${status} · Watched: ${if (device.watched) "yes" else "no"}\n" +
                "Interface ${after.iface} over ${seconds}s\n" +
                "Down ${down} (${rxRate}) · +${rxPackets} packets\n" +
                "Up ${up} (${txRate}) · +${txPackets} packets\n" +
                "Interface drops — RX ${after.rxDrops} / TX ${after.txDrops}\n" +
                "Per-host bytes are not available from Android & root-free interfaces; this is the honest interface window."
        }
        _state.value = _state.value.copy(analyzing = false, analysisResult = body, analysisTargetId = device.id)
    }

    fun clearAnalysis() {
        _state.value = _state.value.copy(analysisResult = null, analysisTargetId = null)
    }

    private fun sortedDevices(): List<Device> = deviceMap.values.sortedWith(compareBy<Device> { !it.online }.thenBy { it.id })

    private fun refreshDevices() {
        _state.value = _state.value.copy(devices = sortedDevices())
    }

    private fun setMessage(m: String) {
        _state.value = _state.value.copy(message = m)
    }

    override fun onCleared() {
        super.onCleared()
        val ni = _state.value.netInfo
        if (ni != null) Limiter.flush(ni.iface, deviceMap.values)
        spoofer?.stopAll()
    }
}
