package com.multinet

import android.app.Application
import com.multinet.database.DownloadDatabase
import com.multinet.database.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MultinetApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // When the app process starts, the download service is not running yet.
        // Any rows still marked DOWNLOADING are stale (from a previous session that
        // was killed, crashed, or the app was reinstalled). Reset them to PAUSED
        // so the user can see them and choose to resume.
        CoroutineScope(Dispatchers.IO).launch {
            val dao = DownloadDatabase.getInstance(this@MultinetApp).downloadDao()
            dao.getActiveDownloads().forEach { download ->
                dao.updateStatus(download.id, DownloadStatus.PAUSED)
            }
        }
    }
}
