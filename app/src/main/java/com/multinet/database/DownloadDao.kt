package com.multinet.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// @Dao = Data Access Object — this interface defines every database operation we need.
// Room generates the actual implementation at build time.
@Dao
interface DownloadDao {

    // Flow<List<...>> means the UI gets a live stream — whenever the DB changes,
    // the list automatically re-emits and the screen recomposes.
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    // Returns all downloads that were mid-flight when the app was killed,
    // so we can resume them on restart.
    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getActiveDownloads(): List<DownloadEntity>

    // suspend = runs on a coroutine (background thread), never blocks the UI thread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    // Only updates bytes and speed — never touches status.
    // Status is only changed explicitly via updateStatus().
    @Query("UPDATE downloads SET downloadedBytes = :bytes, speedBps = :speed, activeMs = :activeMs WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, speed: Long = 0L, activeMs: Long = 0L)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String? = null)

    @Query("UPDATE downloads SET totalBytes = :total, supportsResume = :supportsResume WHERE id = :id")
    suspend fun updateMeta(id: Long, total: Long, supportsResume: Boolean)


    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}
