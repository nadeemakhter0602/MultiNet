package com.multinet.engine

import android.net.Network
import com.multinet.database.ChunkDao
import com.multinet.database.ChunkEntity
import com.multinet.database.ChunkStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MultiNetworkEngine(private val chunkDao: ChunkDao) {

    suspend fun download(
        id: Long,
        url: String,
        filePath: String,
        totalBytes: Long,
        networks: List<Network>,
        stableIds: List<String>,
        displayNames: List<String>,
        minChunkSizeBytes: Long = 256 * 1024L,
        targetChunkCount: Int = 500,
        workerCount: Int = CONNECTIONS,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        File(filePath).parentFile?.mkdirs()
        val file = File(filePath)
        if (!file.exists()) RandomAccessFile(file, "rw").use { it.setLength(totalBytes) }

        data class NetworkClient(val stableId: String, val displayName: String, val client: OkHttpClient)

        val clients = networks.mapIndexed { i, network ->
            NetworkClient(
                stableId    = stableIds.getOrElse(i) { "NET_$i" },
                displayName = displayNames.getOrElse(i) { "" },
                client      = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .socketFactory(network.socketFactory)
                    .build()
            )
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

        supervisorScope {
            clients.flatMapIndexed { netIdx, nc ->
                (0 until workerCount).map { wIdx ->
                    val globalWorkerIdx = netIdx * workerCount + wIdx
                    async {
                        while (true) {
                            val chunk = queue.poll() ?: break
                            val freshChunk = chunkDao.getById(chunk.id) ?: chunk
                            var attempts    = 0
                            var currentClient = nc.client
                            var currentNc = nc
                            // Assign network + worker before downloading so fast chunks
                            // (< 1 second) still get their assignment even if onChunkProgress never fires
                            chunkDao.updateNetwork(freshChunk.id, currentNc.stableId, currentNc.displayName)
                            chunkDao.updateWorker(freshChunk.id, globalWorkerIdx)

                            while (attempts <= clients.size) {
                                try {
                                    downloadChunk(
                                        url    = url,
                                        file   = file,
                                        chunk  = freshChunk,
                                        client = currentClient,
                                        onChunkProgress = { chunkDownloaded ->
                                            chunkDao.updateProgress(freshChunk.id, chunkDownloaded, ChunkStatus.DOWNLOADING)
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
                                    break
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    attempts++
                                    if (attempts >= clients.size) {
                                        chunkDao.updateProgress(freshChunk.id, freshChunk.downloadedBytes, ChunkStatus.PENDING)
                                        throw e
                                    }
                                    // Try next network client — update assignment before retry
                                    val fallbackIdx = (netIdx + attempts) % clients.size
                                    currentNc     = clients[fallbackIdx]
                                    currentClient = currentNc.client
                                    chunkDao.updateNetwork(freshChunk.id, currentNc.stableId, currentNc.displayName)
                                }
                            }
                        }
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
        client: OkHttpClient,
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
}
