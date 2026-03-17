package com.multinet.database

import androidx.room.TypeConverter

// Room can only store primitive types (Int, String, Long, etc.)
// @TypeConverter tells Room how to convert our custom enum to/from String
class Converters {

    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromChunkStatus(status: ChunkStatus): String = status.name

    @TypeConverter
    fun toChunkStatus(value: String): ChunkStatus = ChunkStatus.valueOf(value)
}
