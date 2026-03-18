package com.multinet.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,       // GitHub release page
    val downloadUrl: String?      // direct APK download URL, null if no asset
)

class UpdateService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Returns UpdateInfo if a newer version is available, null if already up to date
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/nadeemakhter0602/MultiNet/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val latestTag = json.getString("tag_name").trimStart('v')
            val releaseUrl = json.getString("html_url")

            // Find the APK asset
            val assets     = json.optJSONArray("assets")
            var downloadUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            if (isNewer(latestTag, currentVersion)) {
                UpdateInfo(latestTag, releaseUrl, downloadUrl)
            } else {
                null
            }
        }
    }

    // Downloads the APK and installs it — calls onProgress(0..100) during download
    suspend fun downloadAndInstall(
        downloadUrl: String,
        version: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request  = Request.Builder().url(downloadUrl).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
        val body  = response.body ?: throw Exception("Empty response")
        val total = body.contentLength()

        val apkFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "MultiNet-v$version.apk"
        )

        var downloaded = 0L
        body.byteStream().use { input ->
            apkFile.outputStream().use { out ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                }
            }
        }

        onProgress(100)
        installApk(apkFile)
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Compares "1.2.3" style version strings
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
