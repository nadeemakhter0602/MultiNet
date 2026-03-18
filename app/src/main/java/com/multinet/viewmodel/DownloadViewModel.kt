package com.multinet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multinet.database.DownloadDatabase
import com.multinet.database.DownloadStatus
import com.multinet.network.NetworkInfo
import com.multinet.network.NetworkMonitor
import com.multinet.repository.DownloadRepository
import com.multinet.repository.DownloadWithChunks
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Represents one chunk's progress for the segmented progress bar
data class ChunkUiState(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long
) {
    val totalBytes: Long get() = endByte - startByte + 1
    // 0.0–1.0 fill for this chunk's segment
    val progress: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
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
    // Non-empty only for multi-connection downloads with known chunk data
    val chunks: List<ChunkUiState>
) {
    val progressPercent: Int get() = ((progress ?: 0f) * 100).toInt()
}

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
        .map { list -> list.map { it.toUiState() } }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addDownload(url: String, fileName: String) {
        viewModelScope.launch { repo.addDownload(url.trim(), fileName.trim()) }
    }

    fun pause(id: Long)  = viewModelScope.launch { repo.pauseDownload(id) }
    fun resume(id: Long) = viewModelScope.launch { repo.resumeDownload(id) }
    fun cancel(id: Long) = viewModelScope.launch { repo.cancelDownload(id) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteDownload(id) }
}

private fun DownloadWithChunks.toUiState() = DownloadUiState(
    id              = download.id,
    fileName        = download.fileName,
    filePath        = download.filePath,
    status          = download.status,
    downloadedBytes = download.downloadedBytes,
    totalBytes      = download.totalBytes,
    errorMessage    = download.errorMessage,
    progress        = if (download.totalBytes > 0) download.downloadedBytes.toFloat() / download.totalBytes else null,
    speedBps        = download.speedBps,
    chunks          = chunks.map { chunk ->
        ChunkUiState(
            index          = chunk.index,
            startByte      = chunk.startByte,
            endByte        = chunk.endByte,
            downloadedBytes = chunk.downloadedBytes
        )
    }
)
