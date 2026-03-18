package com.multinet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multinet.database.ChunkStatus
import com.multinet.database.DownloadDatabase
import com.multinet.database.DownloadStatus
import com.multinet.network.NetworkInfo
import com.multinet.network.NetworkMonitor
import com.multinet.repository.DownloadRepository
import com.multinet.repository.DownloadWithChunks
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChunkUiState(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val networkDisplayName: String,
    val speedBps: Long = 0L
) {
    val totalBytes: Long get() = endByte - startByte + 1
    val progress: Float  get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

data class NetworkProgressState(
    val displayName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    // Chunks assigned to this network — used to render its segmented bar
    val chunks: List<ChunkUiState>
) {
    val progress: Float      get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    val progressPercent: Int get() = (progress * 100).toInt()
}

data class DownloadUiState(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val errorMessage: String?,
    val progress: Float?,
    val speedBps: Long,
    val chunks: List<ChunkUiState>,
    val networkProgress: List<NetworkProgressState>,
    val isMultiNetwork: Boolean
) {
    val progressPercent: Int get() = ((progress ?: 0f) * 100).toInt()
}

// Carries chunk byte snapshots between Flow emissions for speed calculation
private data class SpeedTracker(
    val chunkBytesSnapshot: Map<Long, Long> = emptyMap(),
    val snapshotTime: Long = System.currentTimeMillis(),
    val chunkSpeeds: Map<Long, Long> = emptyMap()   // chunkId → bytes/sec
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val db      = DownloadDatabase.getInstance(app)
    private val repo    = DownloadRepository(
        dao      = db.downloadDao(),
        chunkDao = db.chunkDao(),
        context  = app
    )
    private val monitor = NetworkMonitor(app)

    private val _availableNetworks = MutableStateFlow<List<NetworkInfo>>(emptyList())
    val availableNetworks: StateFlow<List<NetworkInfo>> = _availableNetworks.asStateFlow()

    fun refreshNetworks() {
        _availableNetworks.value = monitor.scan()
    }

    val downloads: StateFlow<List<DownloadUiState>> = repo
        .getAllDownloads()
        // scan() carries a SpeedTracker alongside each new emission so we can compute
        // per-chunk speed = (currentBytes - prevBytes) / elapsedSeconds
        .scan(Pair<List<DownloadWithChunks>, SpeedTracker>(emptyList(), SpeedTracker())) { (_, tracker), newList ->
            val now     = System.currentTimeMillis()
            val elapsed = (now - tracker.snapshotTime) / 1000.0

            val currentBytes = newList
                .flatMap { it.chunks }
                .associate { it.id to it.downloadedBytes }

            // Only refresh speeds if at least 500ms have passed — avoids division by tiny numbers
            val (newSpeeds, newTracker) = if (elapsed >= 0.5) {
                val speeds = currentBytes.mapValues { (id, bytes) ->
                    val prev = tracker.chunkBytesSnapshot[id] ?: bytes
                    ((bytes - prev) / elapsed).toLong().coerceAtLeast(0L)
                }
                speeds to SpeedTracker(currentBytes, now, speeds)
            } else {
                tracker.chunkSpeeds to tracker
            }

            Pair(newList, newTracker)
        }
        .map { (list, tracker) -> list.map { it.toUiState(tracker.chunkSpeeds) } }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addDownload(
        url: String,
        fileName: String,
        selectedNetworks: List<NetworkInfo> = emptyList()
    ) {
        viewModelScope.launch {
            repo.addDownload(url.trim(), fileName.trim(), selectedNetworks)
        }
    }

    fun pause(id: Long)  = viewModelScope.launch { repo.pauseDownload(id) }
    fun resume(id: Long) = viewModelScope.launch { repo.resumeDownload(id) }
    fun cancel(id: Long) = viewModelScope.launch { repo.cancelDownload(id) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteDownload(id) }
}

private fun DownloadWithChunks.toUiState(chunkSpeeds: Map<Long, Long>): DownloadUiState {
    val isMultiNetwork = download.selectedNetworkIds.isNotEmpty()

    val chunkUiStates = chunks.map { chunk ->
        ChunkUiState(
            index              = chunk.index,
            startByte          = chunk.startByte,
            endByte            = chunk.endByte,
            downloadedBytes    = chunk.downloadedBytes,
            networkDisplayName = chunk.networkStableId,  // stable ID used as display label
            speedBps           = chunkSpeeds[chunk.id] ?: 0L
        )
    }

    val networkProgress = if (isMultiNetwork) {
        // Group by current networkStableId — each group = one summary row
        // Exclude chunks without an assignment yet (empty stableId)
        chunkUiStates
            .filter { it.networkDisplayName.isNotEmpty() }
            .groupBy { it.networkDisplayName }
            .map { (stableId, groupChunks) ->
                NetworkProgressState(
                    displayName     = stableId,
                    downloadedBytes = groupChunks.sumOf { it.downloadedBytes },
                    totalBytes      = groupChunks.sumOf { it.totalBytes },
                    speedBps        = groupChunks.sumOf { it.speedBps },
                    chunks          = groupChunks
                )
            }
            .sortedBy { it.displayName }
    } else emptyList()

    return DownloadUiState(
        id              = download.id,
        fileName        = download.fileName,
        filePath        = download.filePath,
        status          = download.status,
        downloadedBytes = download.downloadedBytes,
        totalBytes      = download.totalBytes,
        errorMessage    = download.errorMessage,
        progress        = if (download.totalBytes > 0) download.downloadedBytes.toFloat() / download.totalBytes else null,
        speedBps        = download.speedBps,
        chunks          = chunkUiStates,
        networkProgress = networkProgress,
        isMultiNetwork  = isMultiNetwork
    )
}

