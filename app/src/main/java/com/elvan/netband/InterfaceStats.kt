package com.elvan.netband

import java.io.File

object InterfaceStats {
    data class Counters(
        val iface: String,
        val rxBytes: Long,
        val rxPackets: Long,
        val txBytes: Long,
        val txPackets: Long,
        val rxDrops: Long,
        val txDrops: Long,
        val sampledAtMs: Long
    )

    fun read(iface: String, useRoot: Boolean = false): Counters? {
        return readLines(iface, useRoot)?.let(::parseLine)
    }

    private fun readLines(iface: String, useRoot: Boolean): String? {
        val direct = try {
            File("/proc/net/dev").readLines()
        } catch (_: Exception) {
            emptyList()
        }
        val directLine = direct.firstOrNull { it.trimStart().startsWith("$iface:") }
        if (directLine != null) return directLine
        if (!useRoot) return null
        return RootShell.exec("cat /proc/net/dev 2>/dev/null")
            .firstOrNull { it.trimStart().startsWith("$iface:") }
    }

    private fun parseLine(line: String): Counters? {
        val iface = line.substringBefore(':').trim()
            return try {
            val raw = line.substringAfter(':').trim().split(Regex("\\s+"))
            if (raw.size < 16) return null
            Counters(
                iface = iface,
                rxBytes = raw[0].toLongOrNull() ?: 0,
                rxPackets = raw[1].toLongOrNull() ?: 0,
                rxDrops = raw[3].toLongOrNull() ?: 0,
                txBytes = raw[8].toLongOrNull() ?: 0,
                txPackets = raw[9].toLongOrNull() ?: 0,
                txDrops = raw[11].toLongOrNull() ?: 0,
                sampledAtMs = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun formatBytes(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val decimals = if (value >= 100 || unit == 0) 0 else if (value >= 10) 1 else 2
        return "%.${decimals}f %s".format(value, units[unit])
    }

    fun formatRate(bytesPerSecond: Double): String {
        val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond.coerceAtLeast(0.0)
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val decimals = if (value >= 100 || unit == 0) 0 else if (value >= 10) 1 else 2
        return "%.${decimals}f %s".format(value, units[unit])
    }
}
