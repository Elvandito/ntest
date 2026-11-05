package com.elvan.netband

import android.content.Context
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/**
 * Controls the bundled `netband_arp` native helper (see src/main/cpp/arp_native.c),
 * started as a long-lived root process. Equivalent to the Spoofer class in netband.py,
 * but the raw AF_PACKET work has to happen in a native root process since the JVM/Android
 * app process cannot open raw sockets even when the device is rooted.
 */
class ArpSpoofer(
    private val context: Context,
    private val iface: String,
    private val localIp: String,
    private val localMac: String,
    private val gwIp: String,
    private val gwMac: String
) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    private fun binPath(): String = context.applicationInfo.nativeLibraryDir + "/libnetband_arp.so"

    @Synchronized
    private fun ensureStarted() {
        if (process != null) return
        val bin = binPath()
        val pb = ProcessBuilder("su", "-c", "$bin $iface $localMac $localIp $gwMac $gwIp")
        pb.redirectErrorStream(true)
        process = pb.start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
    }

    @Synchronized
    fun addTarget(ip: String, mac: String) {
        ensureStarted()
        try {
            writer?.write("ADD $ip $mac\n")
            writer?.flush()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun removeTarget(ip: String) {
        try {
            writer?.write("DEL $ip\n")
            writer?.flush()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun stopAll() {
        try {
            writer?.write("QUIT\n")
            writer?.flush()
        } catch (_: Exception) {
        }
        process?.destroy()
        process = null
        writer = null
    }
}
