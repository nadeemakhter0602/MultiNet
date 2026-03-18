package com.multinet.network

import android.net.Network

data class NetworkInfo(
    // Live Android network handle — used to bind OkHttp connections
    val network: Network,
    // Human-readable label shown in the UI e.g. "Wi-Fi", "Mobile Data · SIM 2"
    val displayName: String,
    // Stable identifier for storage — "WIFI", "CELLULAR_0", "ETHERNET"
    val stableId: String,
    // Whether this network charges per byte (cellular = usually true)
    val isMetered: Boolean
)
