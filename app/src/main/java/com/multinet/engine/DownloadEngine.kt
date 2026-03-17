package com.multinet.engine

import com.multinet.database.ChunkDao
import com.multinet.database.ChunkEntity
import com.multinet.database.ChunkStatus
import com.multinet.database.DownloadDao
import com.multinet.database.DownloadStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val BUFFER_SIZE = 8 * 1024
private const val CONNECTIONS = 4   // parallel connections per download

class DownloadEngine(
    private val dao: DownloadDao,
    private val chunkDao: ChunkDao
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Probe server: returns (totalBytes, supportsResume)
    suspend fun probe(url: String): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            val total = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val supportsResume = response.header("Accept-Ranges") == "bytes"
            Pair(total, supportsResume)
        }
    }

    // Entry point: picks multi-connection or single-connection based on server support
    suspend fun download(
        id: Long,
        url: String,
        filePath: String,
        resumeFrom: Long,
        totalBytes: Long,
        supportsResume: Boolean,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        File(filePath).parentFile?.mkdirs()

        if (supportsResume && totalBytes > 0) {
            // Server supports ranges and we know the file size → multi-connection
            downloadMultiConnection(id, url, filePath, totalBytes, onProgress)
        } else if (supportsResume && resumeFrom > 0) {
            // Supports ranges but size unknown → single resumable connection
            downloadSingleResumable(url, File(filePath), resumeFrom, totalBytes, onProgress)
        } else {
            // No range support → simple single connection
            downloadSimple(url, File(filePath), totalBytes, onProgress)
        }
    }

    // ── Multi-connection download ─────────────────────────────────────────────
    //
    // 1. Split the file into CONNECTIONS equal chunks
    // 2. Load any existing chunk progress from DB (resume support)
    // 3. Launch each incomplete chunk as a parallel coroutine
    // 4. Each chunk writes directly to its range in the final file (RandomAccessFile)
    // 5. Aggregate progress across all chunks and report upstream
    //
    // Using coroutineScope{} means: if ANY chunk throws, all other chunks are
    // cancelled automatically (structured concurrency).

    private suspend fun downloadMultiConnection(
        id: Long,
        url: String,
        filePath: String,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {

        // Pre-allocate the file so all chunks can seek and write independently
        val file = File(filePath)
        if (!file.exists()) {
            RandomAccessFile(file, "rw").use { it.setLength(totalBytes) }
        }

        // Load existing chunks (resume) or create them fresh
        var chunks = chunkDao.getChunksFor(id)
        if (chunks.isEmpty()) {
            chunks = buildChunks(id, totalBytes)
            chunkDao.insertAll(chunks)
        }

        // AtomicLong lets multiple coroutines safely add to the same counter
        // without a Mutex. Start at whatever we've already downloaded.
        val totalDownloaded = AtomicLong(chunks.sumOf { it.downloadedBytes })
        var lastReportTime = System.currentTimeMillis()
        var bytesAtLastReport = totalDownloaded.get()

        // coroutineScope{} waits for ALL launched coroutines to finish
        coroutineScope {
            chunks
                .filter { it.status != ChunkStatus.COMPLETE }
                .map { chunk ->
                    // async{} launches each chunk in parallel
                    async {
                        downloadChunk(
                            url       = url,
                            file      = file,
                            chunk     = chunk,
                            onBytes   = { bytesRead ->
                                // Called for every buffer write in this chunk
                                val downloaded = totalDownloaded.addAndGet(bytesRead)
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= 1000) {
                                    val elapsed = (now - lastReportTime) / 1000.0
                                    val speed = ((downloaded - bytesAtLastReport) / elapsed).toLong()
                                    // runBlocking bridges the suspend call from a non-suspend lambda
                                    runBlocking { onProgress(downloaded, totalBytes, speed) }
                                    lastReportTime = now
                                    bytesAtLastReport = downloaded
                                }
                            }
                        )
                    }
                }
                .awaitAll()  // wait for every chunk to finish
        }

        onProgress(totalDownloaded.get(), totalBytes, 0L)
    }

    // Downloads one chunk: bytes [startByte + alreadyDownloaded .. endByte]
    // Writes directly into the correct position of the shared final file.
    private suspend fun downloadChunk(
        url: String,
        file: File,
        chunk: ChunkEntity,
        onBytes: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val resumeOffset = chunk.startByte + chunk.downloadedBytes
        val rangeHeader  = "bytes=$resumeOffset-${chunk.endByte}"

        val request = Request.Builder()
            .url(url)
            .header("Range", rangeHeader)
            .build()

        chunkDao.updateProgress(chunk.id, chunk.downloadedBytes, ChunkStatus.DOWNLOADING)

        client.newCall(request).execute().use { response ->
            if (response.code != 206 && !response.isSuccessful) {
                throw Exception("Chunk ${chunk.index} HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Chunk ${chunk.index} empty body")

            var chunkDownloaded = chunk.downloadedBytes

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

                        // Persist chunk progress every 512 KB so resume is granular
                        if (chunkDownloaded % (512 * 1024) < BUFFER_SIZE) {
                            chunkDao.updateProgress(chunk.id, chunkDownloaded, ChunkStatus.DOWNLOADING)
                        }
                    }
                }
            }

            chunkDao.updateProgress(chunk.id, chunkDownloaded, ChunkStatus.COMPLETE)
        }
    }

    // Split totalBytes evenly into CONNECTIONS chunks
    private fun buildChunks(downloadId: Long, totalBytes: Long): List<ChunkEntity> {
        val chunkSize = totalBytes / CONNECTIONS
        return (0 until CONNECTIONS).map { i ->
            val start = i * chunkSize
            // Last chunk gets any remainder bytes
            val end = if (i == CONNECTIONS - 1) totalBytes - 1 else (start + chunkSize - 1)
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
        client.newCall(request).execute().use { response ->
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

    private suspend fun downloadSingleResumable(
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

        client.newCall(request).execute().use { response ->
            if (response.code != 206 && !response.isSuccessful)
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
