package com.example.runtime

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalLanIpAddresses(): List<String> {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            buildList {
                for (iface in interfaces) {
                    if (!iface.isUp || iface.isLoopback) continue
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: continue
                            if (!host.startsWith("169.254.")) {
                                add(host)
                            }
                        }
                    }
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    fun primaryHostUrl(port: Int, lanHostingEnabled: Boolean): String {
        val lanIp = if (lanHostingEnabled) getLocalLanIpAddresses().firstOrNull() else null
        return if (lanIp != null) "http://$lanIp:$port" else "http://127.0.0.1:$port"
    }
}
