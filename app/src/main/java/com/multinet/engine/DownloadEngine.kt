package com.multinet.engine

import android.net.Network
import com.multinet.database.ChunkDao
import com.multinet.database.DownloadDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Request

const val BUFFER_SIZE = 8 * 1024
const val CONNECTIONS = 4

// Coordinator — probes the server, then delegates to the right engine
// based on the user's network selection (Default vs Multiple).
class DownloadEngine(
    private val dao: DownloadDao,
    private val chunkDao: ChunkDao
) {
    private val defaultEngine = DefaultNetworkEngine(chunkDao)
    // MultiNetworkEngine will be added here

    suspend fun probe(url: String): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        // suspendCancellableCoroutine ensures the call is cancelled immediately if the coroutine is cancelled
        suspendCancellableCoroutine { continuation ->
            val call = defaultEngine.client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { response ->
                    val total = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val supportsResume = response.header("Accept-Ranges") == "bytes"
                    continuation.resume(Pair(total, supportsResume))
                }
            } catch (e: Exception) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        }
    }

    suspend fun download(
        id: Long,
        url: String,
        filePath: String,
        resumeFrom: Long,
        totalBytes: Long,
        supportsResume: Boolean,
        networks: List<Network> = emptyList(),   // empty = Default, non-empty = Multiple
        stableIds: List<String> = emptyList(),
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ) {
        if (networks.isEmpty()) {
            defaultEngine.download(id, url, filePath, resumeFrom, totalBytes, supportsResume, onProgress)
        } else {
            // TODO: multiNetworkEngine.download(...)
            // For now fall back to default until MultiNetworkEngine is built
            defaultEngine.download(id, url, filePath, resumeFrom, totalBytes, supportsResume, onProgress)
        }
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

fun Long.toDisplaySize(): String {
    if (this < 0) return "Unknown"
    return when {
        this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576     -> "%.1f MB".format(this / 1_048_576.0)
        this >= 1_024         -> "%.1f KB".format(this / 1_024.0)
        else                  -> "$this B"
    }
}

fun Long.toDisplaySpeed(): String = "${toDisplaySize()}/s"
