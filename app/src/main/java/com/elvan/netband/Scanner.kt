package com.elvan.netband

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress

object Scanner {

    private const val MAX_HOSTS = 4096

    /** Build the scannable host list for localIp/prefix, capped at MAX_HOSTS for safety. */
    fun buildHostList(localIp: String, prefix: Int): List<String> {
        val hostBits = 32 - prefix
        if (hostBits < 1 || hostBits > 12) return emptyList() // >4094 hosts, same cap as netband.py
        val parts = localIp.split(".").map { it.toInt() }
        val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val mask = -1 shl hostBits
        val network = ipInt and mask
        val broadcast = network or mask.inv()
        val list = mutableListOf<String>()
        for (i in (network + 1) until broadcast) list.add(intToIp(i))
        return list
    }

    private fun intToIp(i: Int): String =
        "${(i shr 24) and 0xFF}.${(i shr 16) and 0xFF}.${(i shr 8) and 0xFF}.${i and 0xFF}"

    /** Parallel ICMP ping sweep (no root needed) to populate the kernel ARP/neighbour cache. */
    suspend fun pingSweep(ips: List<String>, timeoutMs: Int = 800) = withContext(Dispatchers.IO) {
        ips.chunked(32).forEach { batch ->
            batch.map { ip ->
                async {
                    try {
                        InetAddress.getByName(ip).isReachable(timeoutMs)
                    } catch (_: Exception) {
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Read kernel neighbour table: ip -> mac for [iface].
     * Tries a direct /proc read first (works on some Android versions without root),
     * falls back to root if that's blocked.
     */
    fun readArpTable(iface: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = try {
            java.io.File("/proc/net/arp").readLines().drop(1)
        } catch (_: Exception) {
            RootShell.exec("cat /proc/net/arp 2>/dev/null").drop(1)
        }
        for (line in lines) {
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 6) {
                val ip = p[0]
                val mac = p[3].lowercase()
                val dev = p[5]
                if (dev == iface && mac != "00:00:00:00:00:00" && mac.contains(":")) {
                    result[ip] = mac
                }
            }
        }
        return result
    }
}
