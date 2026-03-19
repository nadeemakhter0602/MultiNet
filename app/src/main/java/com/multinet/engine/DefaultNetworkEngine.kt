package com.multinet.engine

import com.multinet.database.ChunkDao
import com.multinet.database.ChunkEntity
import com.multinet.database.ChunkStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

// Handles all downloads on the system's default network (no explicit network binding).
// Supports: chunked multi-connection, single resumable, and simple single connection.
class DefaultNetworkEngine(private val chunkDao: ChunkDao) {

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        id: Long,
        url: String,
        filePath: String,
        resumeFrom: Long,
        totalBytes: Long,
        supportsResume: Boolean,
        minChunkSizeBytes: Long = 256 * 1024L,
        targetChunkCount: Int = 500,
        workerCount: Int = CONNECTIONS,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        File(filePath).parentFile?.mkdirs()

        when {
            supportsResume && totalBytes > 0 ->
                downloadChunked(id, url, filePath, totalBytes, minChunkSizeBytes, targetChunkCount, workerCount, onProgress)
            supportsResume && resumeFrom > 0 ->
                downloadResumable(url, File(filePath), resumeFrom, totalBytes, onProgress)
            else ->
                downloadSimple(url, File(filePath), totalBytes, onProgress)
        }
    }

    // ── Chunked multi-connection ──────────────────────────────────────────────

    private suspend fun downloadChunked(
        id: Long,
        url: String,
        filePath: String,
        totalBytes: Long,
        minChunkSizeBytes: Long = 256 * 1024L,
        targetChunkCount: Int = 500,
        workerCount: Int = CONNECTIONS,
        onProgress: suspend (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {

        val file = File(filePath)
        if (!file.exists()) {
            RandomAccessFile(file, "rw").use { it.setLength(totalBytes) }
        }

        var chunks = chunkDao.getChunksFor(id)
        if (chunks.isEmpty()) {
            chunkDao.insertAll(buildChunks(id, totalBytes, minChunkSizeBytes, targetChunkCount))
            chunks = chunkDao.getChunksFor(id)
        }

        val queue             = java.util.concurrent.ConcurrentLinkedQueue(chunks.filter { it.status != ChunkStatus.COMPLETE })
        val totalDownloaded   = AtomicLong(chunks.sumOf { it.downloadedBytes })
        var lastReportTime    = System.currentTimeMillis()
        var bytesAtLastReport = totalDownloaded.get()

        coroutineScope {
            (0 until workerCount).map { workerIdx ->
                async {
                    while (true) {
                        val chunk = queue.poll() ?: break
                        chunkDao.updateWorker(chunk.id, workerIdx)
                        downloadChunk(
                            url   = url,
                            file  = file,
                            chunk = chunk,
                            onChunkProgress = { chunkDownloaded ->
                                chunkDao.updateProgress(chunk.id, chunkDownloaded, ChunkStatus.DOWNLOADING)
                            },
                            onBytes = { bytesRead ->
                                val downloaded = totalDownloaded.addAndGet(bytesRead)
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= 1000) {
                                    val elapsed = (now - lastReportTime) / 1000.0
                                    val speed = ((downloaded - bytesAtLastReport) / elapsed).toLong()
                                    runBlocking { onProgress(downloaded, totalBytes, speed) }
                                    lastReportTime    = now
                                    bytesAtLastReport = downloaded
                                }
                            }
                        )
                    }
                }
            }.awaitAll()
        }

        onProgress(totalDownloaded.get(), totalBytes, 0L)
    }

    private suspend fun downloadChunk(
        url: String,
        file: File,
        chunk: ChunkEntity,
        onChunkProgress: suspend (chunkDownloaded: Long) -> Unit,
        onBytes: (bytesRead: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val resumeOffset = chunk.startByte + chunk.downloadedBytes

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$resumeOffset-${chunk.endByte}")
            .build()

        chunkDao.updateProgress(chunk.id, chunk.downloadedBytes, ChunkStatus.DOWNLOADING)

        client.executeAsync(request).use { response ->
            if (response.code != 206)
                throw Exception("Chunk ${chunk.index} HTTP ${response.code}")
            val body = response.body ?: throw Exception("Chunk ${chunk.index} empty body")

            var chunkDownloaded  = chunk.downloadedBytes
            var lastProgressTime = System.currentTimeMillis()

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(resumeOffset)
                val buffer = ByteArray(BUFFER_SIZE)

                body.byteStream().use { input ->
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        raf.write(buffer, 0, read)
                        chunkDownloaded += read
                        onBytes(read.toLong())

                        // Update chunk progress every second
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 1000) {
                            onChunkProgress(chunkDownloaded)
                            lastProgressTime = now
                        }
                    }
                }
            }

            chunkDao.updateProgress(chunk.id, chunkDownloaded, ChunkStatus.COMPLETE)
        }
    }

    // Executes an OkHttp request as a cancellable coroutine.
    // When the coroutine is cancelled, call.cancel() is called immediately —
    // this interrupts the blocking execute() instead of waiting for the timeout.
    private suspend fun OkHttpClient.executeAsync(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            try {
                continuation.resume(call.execute())
            } catch (e: Exception) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        }

    private fun buildChunks(
        downloadId: Long,
        totalBytes: Long,
        minChunkSizeBytes: Long = 256 * 1024L,
        targetChunkCount: Int = 500
    ): List<ChunkEntity> {
        val autoChunkSize = totalBytes / targetChunkCount.coerceAtLeast(1)
        val chunkSize     = maxOf(minChunkSizeBytes, autoChunkSize)
        val count         = (totalBytes / chunkSize).toInt().coerceAtLeast(1)
        return (0 until count).map { i ->
            val start = i * chunkSize
            val end   = if (i == count - 1) totalBytes - 1 else start + chunkSize - 1
            ChunkEntity(downloadId = downloadId, index = i, startByte = start, endByte = end)
        }
    }

    // ── Single-connection fallbacks ───────────────────────────────────────────

    private suspend fun downloadSimple(
        url: String,
        file: File,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.executeAsync(request).use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
            val body = response.body ?: throw Exception("Empty response body")

            file.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var downloaded = 0L
                var lastTime = System.currentTimeMillis()
                var bytesAtLastCheck = 0L

                body.byteStream().use { input ->
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 1000) {
                            val speed = ((downloaded - bytesAtLastCheck) / ((now - lastTime) / 1000.0)).toLong()
                            onProgress(downloaded, totalBytes, speed)
                            lastTime = now
                            bytesAtLastCheck = downloaded
                        }
                    }
                }
                onProgress(downloaded, totalBytes, 0L)
            }
        }
    }

    private suspend fun downloadResumable(
        url: String,
        file: File,
        resumeFrom: Long,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$resumeFrom-")
            .build()

        client.executeAsync(request).use { response ->
            if (response.code != 206)
                throw Exception("HTTP ${response.code}: ${response.message}")
            val body = response.body ?: throw Exception("Empty response body")

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(resumeFrom)
                val buffer = ByteArray(BUFFER_SIZE)
                var downloaded = resumeFrom
                var lastTime = System.currentTimeMillis()
                var bytesAtLastCheck = resumeFrom

                body.byteStream().use { input ->
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 1000) {
                            val speed = ((downloaded - bytesAtLastCheck) / ((now - lastTime) / 1000.0)).toLong()
                            onProgress(downloaded, totalBytes, speed)
                            lastTime = now
                            bytesAtLastCheck = downloaded
                        }
                    }
                }
                onProgress(downloaded, totalBytes, 0L)
            }
        }
    }
}
