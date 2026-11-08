package com.elvan.netband

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.NetworkInterface

data class NetInfo(
    val iface: String,
    val localIp: String,
    val gatewayIp: String,
    val prefixLength: Int
)

object NetworkInfo {

    /** Uses ConnectivityManager — no root needed, works on stock Android. */
    fun detect(context: Context): NetInfo? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val lp: LinkProperties = cm.getLinkProperties(network) ?: return null
        val iface = lp.interfaceName ?: return null

        val gateways = lp.routes
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway?.hostAddress }
        val gw = gateways.firstOrNull { !it.contains(":") }
            ?: gateways.firstOrNull()
            ?: return null

        val linkAddr = lp.linkAddresses.firstOrNull { addr ->
            val a = addr.address.hostAddress
            a != null && !a.contains(":")
        } ?: return null

        val localIp = linkAddr.address.hostAddress ?: return null
        return NetInfo(iface, localIp, gw, linkAddr.prefixLength)
    }

    /** Local MAC: try non-root NetworkInterface first, fall back to root file read. */
    fun localMac(iface: String): String {
        try {
            val ni = NetworkInterface.getByName(iface)
            val bytes = ni?.hardwareAddress
            if (bytes != null && bytes.isNotEmpty()) {
                return bytes.joinToString(":") { "%02x".format(it) }
            }
        } catch (_: Exception) {
        }
        val out = RootShell.exec("cat /sys/class/net/$iface/address 2>/dev/null").firstOrNull()
        return out?.trim()?.lowercase()?.ifEmpty { null } ?: "00:00:00:00:00:00"
    }
}
