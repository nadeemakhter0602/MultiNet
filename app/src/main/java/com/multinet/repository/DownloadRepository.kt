package com.multinet.repository

import android.content.Context
import android.os.Environment
import com.multinet.database.ChunkDao
import com.multinet.database.ChunkEntity
import com.multinet.database.DownloadDao
import com.multinet.database.DownloadEntity
import com.multinet.database.DownloadStatus
import com.multinet.network.NetworkInfo
import com.multinet.service.DownloadService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.io.File

// The Repository is the single source of truth for the UI and ViewModels.
// It owns all DB access and decides how/where to save files.
// ViewModels should never touch the DAO directly — always go through here.
// Pairs a download with its chunk list (empty if single-connection)
data class DownloadWithChunks(
    val download: DownloadEntity,
    val chunks: List<ChunkEntity>
)

class DownloadRepository(
    private val dao: DownloadDao,
    private val chunkDao: ChunkDao,
    private val context: Context
) {

    // Combines the download list with live chunk data for each download.
    // flatMapLatest: whenever the download list changes, rebuild the combined flow.
    // combine: whenever ANY chunk updates, the whole list re-emits.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllDownloads(): Flow<List<DownloadWithChunks>> =
        dao.getAllDownloads().flatMapLatest { downloads ->
            if (downloads.isEmpty()) return@flatMapLatest flowOf(emptyList())

            // Build one Flow<List<ChunkEntity>> per download, then combine them all
            val chunkFlows = downloads.map { chunkDao.observeChunksFor(it.id) }

            combine(chunkFlows) { chunkArrays ->
                downloads.mapIndexed { i, download ->
                    DownloadWithChunks(download, chunkArrays[i])
                }
            }
        }

    // Add a new download to the queue and kick off the service.
    // Returns the new DB row ID.
    suspend fun addDownload(
        url: String,
        fileName: String,
        selectedNetworks: List<NetworkInfo> = emptyList(),
        minChunkSizeBytes: Long = 256 * 1024L,
        targetChunkCount: Int = 500,
        workerCount: Int = 10
    ): Long {
        val filePath   = buildFilePath(fileName)
        val networkIds = selectedNetworks.joinToString(",") { it.stableId }
        val entity = DownloadEntity(
            url                = url,
            filePath           = filePath,
            fileName           = fileName,
            status             = DownloadStatus.QUEUED,
            selectedNetworkIds = networkIds,
            minChunkSizeBytes  = minChunkSizeBytes,
            targetChunkCount   = targetChunkCount,
            workerCount        = workerCount
        )
        val id = dao.insert(entity)
        context.startService(DownloadService.startIntent(context, id, selectedNetworks))
        return id
    }

    suspend fun pauseDownload(id: Long) {
        context.startService(DownloadService.pauseIntent(context, id))
    }

    suspend fun resumeDownload(id: Long) {
        val download = dao.getById(id) ?: return
        if (download.selectedNetworkIds.isNotEmpty()) {
            // Multi-network mode — pass stored stable IDs so the service can resolve live networks
            val stableIds = download.selectedNetworkIds.split(",")
            context.startService(DownloadService.multiResumeIntent(context, id, stableIds))
        } else {
            // Default mode
            context.startService(DownloadService.resumeIntent(context, id))
        }
    }

    suspend fun cancelDownload(id: Long) {
        context.startService(DownloadService.cancelIntent(context, id))
        // Delete the partial file from disk
        val download = dao.getById(id)
        download?.filePath?.let { File(it).delete() }
        dao.delete(id)
    }

    suspend fun deleteDownload(id: Long) {
        val download = dao.getById(id) ?: return
        // Only delete file if download didn't complete (completed file is user's)
        if (download.status != DownloadStatus.COMPLETED) {
            File(download.filePath).delete()
        }
        dao.delete(id)
    }

    // Builds the full path: public Downloads folder / fileName
    // Appends (1), (2) etc. if the file already exists
    private fun buildFilePath(fileName: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()

        var file = File(dir, fileName)
        if (!file.exists()) return file.absolutePath

        // Split "video.mp4" into ("video", ".mp4") and try "video (1).mp4", etc.
        val dot = fileName.lastIndexOf('.')
        val name = if (dot >= 0) fileName.substring(0, dot) else fileName
        val ext  = if (dot >= 0) fileName.substring(dot)    else ""

        var counter = 1
        while (file.exists()) {
            file = File(dir, "$name ($counter)$ext")
            counter++
        }
        return file.absolutePath
    }
}
