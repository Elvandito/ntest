package com.elvan.netband

enum class Direction { UP, DOWN, BOTH }

data class Device(
    var id: Int,
    var ip: String,
    var mac: String,
    var hostname: String = "",
    var online: Boolean = true,
    var limitRate: String? = null,   // e.g. "512kbit", null = not rate-limited
    var direction: Direction = Direction.BOTH,
    var blocked: Boolean = false
)
