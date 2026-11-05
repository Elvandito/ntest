package com.elvan.netband

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Port of netband.py's devices.json store (org.json instead of a DB — matches original scope). */
object DeviceStore {
    private const val FILE = "devices.json"

    fun load(context: Context): MutableMap<String, Device> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return mutableMapOf()
        return try {
            val arr = JSONArray(f.readText())
            val map = mutableMapOf<String, Device>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = Device(
                    id = o.getInt("id"),
                    ip = o.getString("ip"),
                    mac = o.getString("mac"),
                    hostname = o.optString("hostname", ""),
                    online = o.optBoolean("online", true),
                    limitRate = o.optString("limitRate", "").ifEmpty { null },
                    direction = Direction.valueOf(o.optString("direction", "BOTH")),
                    blocked = o.optBoolean("blocked", false)
                )
                map[d.mac] = d
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun save(context: Context, devices: Map<String, Device>) {
        val arr = JSONArray()
        devices.values.forEach { d ->
            val o = JSONObject()
            o.put("id", d.id)
            o.put("ip", d.ip)
            o.put("mac", d.mac)
            o.put("hostname", d.hostname)
            o.put("online", d.online)
            o.put("limitRate", d.limitRate ?: "")
            o.put("direction", d.direction.name)
            o.put("blocked", d.blocked)
            arr.put(o)
        }
        File(context.filesDir, FILE).writeText(arr.toString())
    }
}
