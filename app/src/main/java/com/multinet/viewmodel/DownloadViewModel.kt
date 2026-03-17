package com.multinet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multinet.database.DownloadDatabase
import com.multinet.database.DownloadEntity
import com.multinet.database.DownloadStatus
import com.multinet.repository.DownloadRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// UI state for a single download card shown in the list
data class DownloadUiState(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val errorMessage: String?,
    // Progress 0.0–1.0, or null if total size is unknown
    val progress: Float?,
    val speedBps: Long
) {
    // Human-readable percentage for the UI
    val progressPercent: Int get() = ((progress ?: 0f) * 100).toInt()
}

// AndroidViewModel gets the Application context — needed to access the DB and repo
// without leaking an Activity reference.
class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(
        dao     = DownloadDatabase.getInstance(app).downloadDao(),
        context = app
    )

    // StateFlow<List<DownloadUiState>> — the UI observes this
    // stateIn() converts the cold Flow from Room into a hot StateFlow
    val downloads: StateFlow<List<DownloadUiState>> = repo
        .getAllDownloads()
        .map { list -> list.map { it.toUiState() } }
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(5_000),
            initialValue     = emptyList()
        )

    // ── Actions called by the UI ──────────────────────────────────────────────

    fun addDownload(url: String, fileName: String) {
        // launch{} starts a coroutine tied to the ViewModel's lifecycle
        viewModelScope.launch {
            repo.addDownload(url.trim(), fileName.trim())
        }
    }

    fun pause(id: Long) = viewModelScope.launch { repo.pauseDownload(id) }

    fun resume(id: Long) = viewModelScope.launch { repo.resumeDownload(id) }

    fun cancel(id: Long) = viewModelScope.launch { repo.cancelDownload(id) }

    fun delete(id: Long) = viewModelScope.launch { repo.deleteDownload(id) }
}

// Extension function: maps the DB entity → the UI state the screen needs
private fun DownloadEntity.toUiState() = DownloadUiState(
    id              = id,
    fileName        = fileName,
    filePath        = filePath,
    status          = status,
    downloadedBytes = downloadedBytes,
    totalBytes      = totalBytes,
    errorMessage    = errorMessage,
    progress        = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else null,
    speedBps        = speedBps
)
