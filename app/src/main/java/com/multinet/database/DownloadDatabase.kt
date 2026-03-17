package com.multinet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadEntity::class, ChunkEntity::class],
    version  = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : androidx.room.RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun chunkDao(): ChunkDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN speedBps INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Add the chunks table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chunks (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        downloadId      INTEGER NOT NULL,
                        `index`         INTEGER NOT NULL,
                        startByte       INTEGER NOT NULL,
                        endByte         INTEGER NOT NULL,
                        downloadedBytes INTEGER NOT NULL DEFAULT 0,
                        status          TEXT    NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY (downloadId) REFERENCES downloads(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chunks_downloadId ON chunks(downloadId)")
            }
        }

        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "multinet.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
