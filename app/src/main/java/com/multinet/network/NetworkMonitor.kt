package com.multinet.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Simple one-shot network scanner. Call scan() to get currently available networks.
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun scan(): List<NetworkInfo> {
        var cellularCount = 0
        val result = mutableListOf<NetworkInfo>()

        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@forEach

            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

            val info = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkInfo(
                    network     = network,
                    displayName = "Wi-Fi",
                    stableId    = "WIFI",
                    isMetered   = isMetered
                )
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkInfo(
                    network     = network,
                    displayName = if (cellularCount == 0) "Mobile Data" else "Mobile Data · SIM ${cellularCount + 1}",
                    stableId    = "CELLULAR_$cellularCount",
                    isMetered   = isMetered
                ).also { cellularCount++ }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkInfo(
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
