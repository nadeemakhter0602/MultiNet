package com.multinet.network

import android.net.Network

// Shortens stable IDs for display: "CELLULAR_0" → "CELL_0", others unchanged
fun String.toShortStableId(): String = when {
    startsWith("CELLULAR_") -> "CELL_${removePrefix("CELLULAR_")}"
    else -> this
}

data class NetworkInfo(
    // Live Android network handle — used to bind OkHttp connections
    val network: Network,
    // Human-readable label e.g. "Wi-Fi", "Mobile Data · SIM 2"
    val displayName: String,
    // Stable identifier for storage — "WIFI", "CELLULAR_0", "ETHERNET"
    val stableId: String,
    // Whether this network charges per byte (cellular = usually true)
    val isMetered: Boolean
)
