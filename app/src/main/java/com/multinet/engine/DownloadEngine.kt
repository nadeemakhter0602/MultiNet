package com.multinet.engine

import android.net.Network
import com.multinet.database.ChunkDao
import com.multinet.database.DownloadDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val BUFFER_SIZE = 8 * 1024
const val CONNECTIONS = 4

// Thin coordinator — probes the server and delegates to the right engine:
//   networks.isEmpty() → DefaultNetworkEngine
//   networks.isNotEmpty() → MultiNetworkEngine
class DownloadEngine(
    private val dao: DownloadDao,
    private val chunkDao: ChunkDao
) {
    private val defaultEngine = DefaultNetworkEngine(chunkDao)
    private val multiEngine   = MultiNetworkEngine(chunkDao)

    suspend fun probe(url: String): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
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
        networks: List<Network> = emptyList(),
        stableIds: List<String> = emptyList(),
        displayNames: List<String> = emptyList(),
        minChunkSizeBytes: Long = 256 * 1024L,
        targetChunkCount: Int = 2000,
        workerCount: Int = CONNECTIONS,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ) {
        if (networks.isEmpty()) {
            defaultEngine.download(id, url, filePath, resumeFrom, totalBytes, supportsResume, minChunkSizeBytes, targetChunkCount, workerCount, onProgress)
        } else {
            multiEngine.download(id, url, filePath, totalBytes, networks, stableIds, displayNames, minChunkSizeBytes, targetChunkCount, workerCount, onProgress)
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
