package com.multinet.database

import androidx.room.*

@Dao
interface ChunkDao {

    @Query("SELECT * FROM chunks WHERE downloadId = :downloadId ORDER BY `index`")
    suspend fun getChunksFor(downloadId: Long): List<ChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("UPDATE chunks SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, status: ChunkStatus)

    @Query("DELETE FROM chunks WHERE downloadId = :downloadId")
    suspend fun deleteFor(downloadId: Long)
}
