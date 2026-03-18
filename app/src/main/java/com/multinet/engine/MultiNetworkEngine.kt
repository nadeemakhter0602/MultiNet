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
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        File(filePath).parentFile?.mkdirs()
        val file = File(filePath)
        if (!file.exists()) RandomAccessFile(file, "rw").use { it.setLength(totalBytes) }

        data class NetworkClient(val stableId: String, val displayName: String, val client: OkHttpClient)

        val resolvedClients = networks.mapIndexed { i, network ->
            NetworkClient(
                stableId    = stableIds.getOrElse(i) { "NET_$i" },
                displayName = displayNames.getOrElse(i) { "" },
                client      = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .socketFactory(network.socketFactory)
                    .build()
            )
        }.associateBy { it.stableId }

        val defaultClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        var chunks = chunkDao.getChunksFor(id)
        if (chunks.isEmpty()) {
            chunkDao.insertAll(buildChunks(id, totalBytes, stableIds, displayNames))
            chunks = chunkDao.getChunksFor(id)
        } else {
            val incomplete = chunks.filter { it.status != ChunkStatus.COMPLETE }
            incomplete.forEachIndexed { i, chunk ->
                val sid = stableIds[i % stableIds.size]
                val nc  = resolvedClients[sid]
                if (nc != null) chunkDao.updateNetwork(chunk.id, nc.stableId, nc.displayName)
            }
            chunks = chunkDao.getChunksFor(id)
        }

        val totalDownloaded   = AtomicLong(chunks.sumOf { it.downloadedBytes })
        var lastReportTime    = System.currentTimeMillis()
        var bytesAtLastReport = totalDownloaded.get()

        supervisorScope {
            chunks
                .filter { it.status != ChunkStatus.COMPLETE }
                .mapIndexed { i, chunk ->
                    async {
                        val clientsToTry = stableIds.indices.map { j ->
                            val sid = stableIds[j]
                            resolvedClients[sid] ?: NetworkClient(sid, sid, defaultClient)
                        }
                        var clientIndex = i % clientsToTry.size
                        var attempts    = 0

                        while (attempts < clientsToTry.size) {
                            val nc         = clientsToTry[clientIndex]
                            val freshChunk = chunkDao.getById(chunk.id) ?: chunk
                            android.util.Log.d("MultiNet.Chunk", "Chunk ${chunk.index} attempt $attempts on ${nc.stableId}")
                            if (freshChunk.networkStableId != nc.stableId) {
                                chunkDao.updateNetwork(freshChunk.id, nc.stableId, nc.displayName)
                            }
                            try {
                                downloadChunk(
                                    url    = url,
                                    file   = file,
                                    chunk  = freshChunk,
                                    client = nc.client,
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
                                android.util.Log.e("MultiNet.Chunk",
                                    "Chunk ${chunk.index} failed on ${nc.stableId}: ${e::class.simpleName} - ${e.message}")
                                attempts++
                                clientIndex = (clientIndex + 1) % clientsToTry.size
                                if (attempts >= clientsToTry.size) throw e
                            }
                        }
                    }
                }
                .awaitAll()
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
            if (response.code != 206 && !response.isSuccessful)
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
        stableIds: List<String>,
        displayNames: List<String>
    ): List<ChunkEntity> {
        val chunkSize = totalBytes / CONNECTIONS
        return (0 until CONNECTIONS).map { i ->
            val start = i * chunkSize
            val end   = if (i == CONNECTIONS - 1) totalBytes - 1 else start + chunkSize - 1
            ChunkEntity(
                downloadId         = downloadId,
                index              = i,
                startByte          = start,
                endByte            = end,
                networkStableId    = stableIds.getOrElse(i % stableIds.size.coerceAtLeast(1)) { "" },
                networkDisplayName = displayNames.getOrElse(i % displayNames.size.coerceAtLeast(1)) { "" }
            )
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
