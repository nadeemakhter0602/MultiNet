package com.multinet.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {

    @Query("SELECT * FROM chunks WHERE downloadId = :downloadId ORDER BY `index`")
    suspend fun getChunksFor(downloadId: Long): List<ChunkEntity>

    // Live version — emits a new list every time any chunk for this download changes
    @Query("SELECT * FROM chunks WHERE downloadId = :downloadId ORDER BY `index`")
    fun observeChunksFor(downloadId: Long): Flow<List<ChunkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("UPDATE chunks SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, status: ChunkStatus)

    @Query("UPDATE chunks SET networkStableId = :stableId, networkDisplayName = :displayName WHERE id = :id")
    suspend fun updateNetwork(id: Long, stableId: String, displayName: String = "")

    @Query("UPDATE chunks SET workerIndex = :workerIndex WHERE id = :id")
    suspend fun updateWorker(id: Long, workerIndex: Int)

    @Query("SELECT * FROM chunks WHERE id = :id")
    suspend fun getById(id: Long): ChunkEntity?

    @Query("DELETE FROM chunks WHERE downloadId = :downloadId")
    suspend fun deleteFor(downloadId: Long)
}
