package com.multinet.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities


// Simple one-shot network scanner. Call scan() to get currently available networks.
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun isVpnActive(): Boolean {
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    fun scan(): List<NetworkInfo> {
        var cellularCount = 0
        val result = mutableListOf<NetworkInfo>()

        @Suppress("DEPRECATION")
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach

            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

            // Skip VPN networks entirely — VPN intercepts all traffic regardless
            // of socket binding, so multi-network mode doesn't work with VPN active
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@forEach

            val info = when {
                // WiFi: require internet capability
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkInfo(
                    network     = network,
                    displayName = "Wi-Fi",
                    stableId    = "WIFI",
                    isMetered   = isMetered
                )
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) -> NetworkInfo(
                    network     = network,
                    displayName = if (cellularCount == 0) "Mobile Data" else "Mobile Data · SIM ${cellularCount + 1}",
                    stableId    = "CELLULAR_$cellularCount",
                    isMetered   = isMetered
                ).also { cellularCount++ }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkInfo(
                    network     = network,
                    displayName = "Ethernet",
                    stableId    = "ETHERNET",
                    isMetered   = isMetered
                )
                else -> null
            } ?: return@forEach

            result.add(info)
        }

        return result.sortedBy { it.stableId }
    }
}
