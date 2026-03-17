package com.multinet.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.multinet.MainActivity
import com.multinet.database.DownloadDatabase
import com.multinet.database.DownloadStatus
import com.multinet.engine.DownloadEngine
import com.multinet.engine.toDisplaySize
import com.multinet.engine.toDisplaySpeed
import kotlinx.coroutines.*

// A ForegroundService runs in the background and shows a persistent notification.
// Android won't kill it (unlike a regular background service) while it's in the foreground.
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "multinet_downloads"
        const val NOTIFICATION_ID = 1

        // Intent actions — callers send these to tell the service what to do
        const val ACTION_START  = "com.multinet.START"
        const val ACTION_PAUSE  = "com.multinet.PAUSE"
        const val ACTION_RESUME = "com.multinet.RESUME"
        const val ACTION_CANCEL = "com.multinet.CANCEL"

        const val EXTRA_DOWNLOAD_ID = "download_id"

        // Convenience builders so callers don't have to build Intents manually
        fun startIntent(ctx: Context, id: Long) =
            Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }

        fun pauseIntent(ctx: Context, id: Long) =
            Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }

        fun resumeIntent(ctx: Context, id: Long) =
            Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }

        fun cancelIntent(ctx: Context, id: Long) =
            Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }
    }

    // SupervisorJob: if one child coroutine fails, others keep running
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Map of downloadId → coroutine Job, so we can cancel individual downloads
    private val activeJobs = mutableMapOf<Long, Job>()

    private lateinit var dao: com.multinet.database.DownloadDao
    private lateinit var chunkDao: com.multinet.database.ChunkDao
    private lateinit var engine: DownloadEngine
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        val db = DownloadDatabase.getInstance(this)
        dao = db.downloadDao()
        chunkDao = db.chunkDao()
        engine = DownloadEngine(dao, chunkDao)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    // Called every time someone sends an Intent to this service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L

        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> if (id != -1L) startDownload(id)
            ACTION_PAUSE  -> if (id != -1L) pauseDownload(id)
            ACTION_CANCEL -> if (id != -1L) cancelDownload(id)
        }

        // START_STICKY: if Android kills this service, restart it with the last intent
        return START_STICKY
    }

    private fun startDownload(id: Long) {
        // Don't start if already running
        if (activeJobs[id]?.isActive == true) return

        val job = serviceScope.launch {
            val download = dao.getById(id) ?: return@launch

            // Update status to DOWNLOADING
            dao.updateStatus(id, DownloadStatus.DOWNLOADING)

            // Show the foreground notification (required before any work on Android 9+)
            startForegroundIfNeeded()

            try {
                // 1. Probe the server for file size and resume support
                val (total, supportsResume) = engine.probe(download.url)
                dao.updateMeta(id, total, supportsResume)

                // 2. Figure out where to resume from
                val resumeFrom = if (supportsResume) download.downloadedBytes else 0L

                // 3. Download, updating the DB and notification on each progress tick
                engine.download(
                    id         = id,
                    url        = download.url,
                    filePath   = download.filePath,
                    resumeFrom = resumeFrom,
                    totalBytes = total,
                    supportsResume = supportsResume
                ) { downloaded, totalBytes, speedBps ->
                    dao.updateProgress(id, downloaded, speedBps)
                    updateNotification(download.fileName, downloaded, totalBytes, speedBps)
                }

                // Natural completion
                dao.updateStatus(id, DownloadStatus.COMPLETED)
                activeJobs.remove(id)
                stopSelfIfIdle()

            } catch (e: CancellationException) {
                // Externally cancelled (pause/cancel) — only remove from map.
                // pauseDownload/cancelDownload will set the status and stop the service
                // after cancelAndJoin() returns.
                activeJobs.remove(id)

            } catch (e: Exception) {
                dao.updateStatus(id, DownloadStatus.FAILED, e.message)
                activeJobs.remove(id)
                stopSelfIfIdle()
            }
        }

        activeJobs[id] = job
    }

    private fun pauseDownload(id: Long) {
        serviceScope.launch {
            activeJobs[id]?.cancelAndJoin()
            activeJobs.remove(id)
            dao.updateStatus(id, DownloadStatus.PAUSED)
            // Don't stop the service — just hide the notification.
            // This way resume can hit the already-running service with no restart.
            if (activeJobs.isEmpty()) stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun cancelDownload(id: Long) {
        serviceScope.launch {
            activeJobs[id]?.cancelAndJoin()
            activeJobs.remove(id)
            dao.updateStatus(id, DownloadStatus.FAILED, "Cancelled")
            chunkDao.deleteFor(id)
            stopSelfIfIdle()
        }
    }

    // Stop the service (and remove the notification) if nothing is downloading
    private fun stopSelfIfIdle() {
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    // Notification channels are required on Android 8+.
    // You register a channel once; users can change its settings in system settings.
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.multinet.R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW  // LOW = no sound, shows in status bar
        ).apply {
            description = getString(com.multinet.R.string.channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,   // 0–100, or -1 for indeterminate
        max: Int = 100
    ): Notification {
        // Tapping the notification opens the app
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppIntent)
            .setOngoing(true)   // user can't dismiss it while download is active
            .apply {
                if (progress == -1) {
                    setProgress(0, 0, true)   // indeterminate spinner
                } else {
                    setProgress(max, progress, false)
                }
            }
            .build()
    }

    private fun startForegroundIfNeeded() {
        val notification = buildNotification("Multinet", "Starting download…", -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        fileName: String,
        downloaded: Long,
        total: Long,
        speedBps: Long
    ) {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else -1
        val text = buildString {
            append(downloaded.toDisplaySize())
            if (total > 0) append(" / ${total.toDisplaySize()}  ($progress%)")
            if (speedBps > 0) append("  •  ${speedBps.toDisplaySpeed()}")
        }
        val notification = buildNotification(fileName, text, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Service has no bound interface — we communicate via Intents only
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Cancel all coroutines when the service is destroyed
        serviceScope.cancel()
        super.onDestroy()
    }
}
