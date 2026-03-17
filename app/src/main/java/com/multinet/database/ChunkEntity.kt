package com.multinet.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ChunkStatus {
    PENDING,      // not started yet
    DOWNLOADING,  // in progress
    COMPLETE      // finished successfully
}

// One row per chunk per download.
// e.g. a 4-connection download of a 100 MB file creates 4 ChunkEntity rows.
@Entity(
    tableName = "chunks",
    foreignKeys = [ForeignKey(
        entity        = DownloadEntity::class,
        parentColumns = ["id"],
        childColumns  = ["downloadId"],
        onDelete      = ForeignKey.CASCADE   // delete chunks when download is deleted
    )],
    indices = [Index("downloadId")]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val downloadId: Long,

    // Which chunk this is (0, 1, 2, 3 for 4 connections)
    val index: Int,

    // Absolute byte range in the full file
    val startByte: Long,
    val endByte: Long,

    // How many bytes of this chunk are already on disk (for resume)
    val downloadedBytes: Long = 0L,

    val status: ChunkStatus = ChunkStatus.PENDING
)
