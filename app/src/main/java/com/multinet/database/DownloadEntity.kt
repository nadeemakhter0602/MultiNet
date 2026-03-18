package com.multinet.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// @Entity tells Room this class maps to a database table called "downloads"
@Entity(tableName = "downloads")
data class DownloadEntity(

    // @PrimaryKey(autoGenerate = true) means Room assigns an ID automatically
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val url: String,

    // Where the file will be saved on disk
    val filePath: String,

    // Original filename shown in the UI
    val fileName: String,

    // Total file size in bytes (-1 if server didn't tell us)
    val totalBytes: Long = -1L,

    // How many bytes we've downloaded so far
    val downloadedBytes: Long = 0L,

    // Whether the server supports pause/resume (HTTP Range header)
    val supportsResume: Boolean = false,

    val status: DownloadStatus = DownloadStatus.QUEUED,

    // Unix timestamp when download was added
    val createdAt: Long = System.currentTimeMillis(),

    // Error message if status == FAILED
    val errorMessage: String? = null,

    // Current download speed in bytes/sec (0 when not actively downloading)
    val speedBps: Long = 0L,

    // Comma-separated stable network IDs chosen by the user (empty = default)
    // e.g. "WIFI,CELLULAR_0"
    val selectedNetworkIds: String = "",

    // Accumulated active download time in milliseconds (pausing stops the clock)
    val activeMs: Long = 0L
)
