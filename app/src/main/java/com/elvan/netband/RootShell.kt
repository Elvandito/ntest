package com.elvan.netband

import com.topjohnwu.superuser.Shell

/** Thin wrapper around libsu for one-shot root commands (tc, iptables, cat, ...). */
object RootShell {
    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create())
    }

    fun isRootAvailable(): Boolean = try {
        Shell.getShell().isRoot
    } catch (e: Exception) {
        false
    }

    fun exec(cmd: String): List<String> = Shell.cmd(cmd).exec().out

    fun execBool(cmd: String): Boolean = Shell.cmd(cmd).exec().isSuccess
}
