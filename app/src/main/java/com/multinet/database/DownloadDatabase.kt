package com.multinet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters


@Database(
    entities = [DownloadEntity::class, ChunkEntity::class],
    version  = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : androidx.room.RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun chunkDao(): ChunkDao

    companion object {

        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "multinet.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
