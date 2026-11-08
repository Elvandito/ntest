package com.elvan.netband

import android.util.Log
import java.io.IOException

/**
 * Runs root commands through `su -c`.
 *
 * The test emulator exposes a small su client that proxies to a root daemon. Some
 * su implementations (including the AOSP one) do not behave like a persistent
 * interactive shell, so we deliberately use one-shot `su -c` invocations here
 * instead of relying on libsu's interactive shell probe.
 */
object RootShell {
    private const val TAG = "NetBandRoot"
    private const val MARKER = "__NETBAND_EXIT__"

    private fun commandWithExitMarker(cmd: String): String =
        "$cmd; printf '\\n$MARKER:%s\\n' \"${'$'}?\""

    private fun runSu(cmd: String): Pair<Int, List<String>> {
        var lastError: IOException? = null
        for (su in listOf("su", "/system/bin/su", "/system/xbin/su")) {
            try {
                val process = ProcessBuilder(su, "-c", commandWithExitMarker(cmd))
                    .redirectErrorStream(true)
                    .start()
                val lines = process.inputStream.bufferedReader().use { it.readLines() }
                val exit = process.waitFor()
                var code = exit
                val output = ArrayList<String>(lines.size)
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("$MARKER:")) {
                        code = trimmed.substringAfter(':').toIntOrNull() ?: exit
                    } else {
                        output.add(line)
                    }
                }
                return code to output
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("su is unavailable")
    }

    fun isRootAvailable(): Boolean = try {
        val (code, output) = runSu("id")
        val root = code == 0 && output.any { it.contains("uid=0") }
        Log.d(TAG, "su -c id code=$code root=$root out=$output")
        root
    } catch (e: Exception) {
        Log.e(TAG, "root check failed", e)
        false
    }

    fun exec(cmd: String): List<String> = try {
        runSu(cmd).second
    } catch (e: Exception) {
        Log.e(TAG, "command failed: $cmd", e)
        emptyList()
    }

    fun execBool(cmd: String): Boolean = try {
        runSu(cmd).first == 0
    } catch (e: Exception) {
        Log.e(TAG, "command failed: $cmd", e)
        false
    }
}
