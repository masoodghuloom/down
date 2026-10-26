package com.savevideo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that downloads a single video with yt-dlp and copies the
 * result into the phone's gallery (Movies/SaveVideo). Each shared link starts
 * one job; several can run at once.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val active = AtomicInteger(0)
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val PROGRESS_CHANNEL = "downloads_progress"
        private const val DONE_CHANNEL = "downloads_done"
        private const val FG_ID = 1
        private var initialized = false
        private val initLock = Any()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        startForegroundCompat(buildFg(active.get() + 1))

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finishIfIdle(startId)
            return START_NOT_STICKY
        }

        val notifId = 1000 + startId
        active.incrementAndGet()

        scope.launch {
            try {
                ensureEngineReady()
                val file = runWithRetry(url, notifId)
                progress(notifId, "Saving to gallery…", -1)
                saveToGallery(file)
                file.parentFile?.deleteRecursively()
                done(notifId, "Saved to your gallery ✓", url)
            } catch (e: Throwable) {
                done(notifId, friendlyError(e), url)
            } finally {
                active.decrementAndGet()
                finishIfIdle(startId)
            }
        }
        return START_NOT_STICKY
    }

    // ----- yt-dlp -----------------------------------------------------------

    private fun ensureEngineReady() {
        synchronized(initLock) {
            if (!initialized) {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                initialized = true
            }
        }
    }

    /** Download once; if it fails, refresh yt-dlp (sites change often) and try again. */
    private fun runWithRetry(url: String, notifId: Int): File {
        return try {
            download(url, notifId)
        } catch (first: Throwable) {
            try {
                progress(notifId, "Updating downloader…", -1)
                YoutubeDL.getInstance().updateYoutubeDL(this)
            } catch (_: Throwable) { /* keep going with what we have */ }
            download(url, notifId)
        }
    }

    private fun download(url: String, notifId: Int): File {
        val jobDir = File(cacheDir, "dl/$notifId").apply {
            deleteRecursively(); mkdirs()
        }

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--restrict-filenames")
            addOption("-f", "bv*+ba/b")
            addOption("--merge-output-format", "mp4")
            addOption("-o", File(jobDir, "%(title).60s.%(ext)s").absolutePath)
        }

        progress(notifId, "Fetching video…", 0)
        YoutubeDL.getInstance().execute(request) { pct, _, _ ->
            progress(notifId, "Downloading… ${pct.toInt().coerceIn(0, 100)}%", pct.toInt())
        }

        val files = jobDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
        return files?.firstOrNull { it.extension.equals("mp4", true) }
            ?: files?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("Nothing was downloaded")
    }

    // ----- Save to the gallery ---------------------------------------------

    private fun saveToGallery(file: File) {
        val name = file.name
        val mime = when (file.extension.lowercase()) {
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/SaveVideo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri: Uri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: throw IllegalStateException("Could not create gallery entry")

            contentResolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SaveVideo"
            ).apply { mkdirs() }
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(this, arrayOf(dest.absolutePath), arrayOf(mime), null)
        }
    }

    // ----- Notifications ----------------------------------------------------

    private fun createChannels() {
        if (nm.getNotificationChannel(PROGRESS_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(PROGRESS_CHANNEL, "Download progress", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (nm.getNotificationChannel(DONE_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(DONE_CHANNEL, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun buildFg(count: Int): Notification =
        NotificationCompat.Builder(this, PROGRESS_CHANNEL)
            .setContentTitle(if (count > 1) "Saving $count videos…" else "Saving video…")
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

    private fun progress(id: Int, text: String, percent: Int) {
        val b = NotificationCompat.Builder(this, PROGRESS_CHANNEL)
            .setContentTitle("Save Video")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
        if (percent in 0..100) b.setProgress(100, percent, false) else b.setProgress(0, 0, true)
        nm.notify(id, b.build())
    }

    private fun done(id: Int, text: String, url: String) {
        val notif = NotificationCompat.Builder(this, DONE_CHANNEL)
            .setContentTitle("Save Video")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_download)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(id, notif)
    }

    // ----- Service lifecycle ------------------------------------------------

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FG_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FG_ID, notification)
        }
    }

    private fun finishIfIdle(startId: Int) {
        if (active.get() <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: "unknown error"
        return when {
            msg.contains("login", true) || msg.contains("private", true) ->
                "Couldn't download — this video may be private or need a login."
            msg.contains("Unsupported URL", true) ->
                "That link isn't supported."
            else -> "Couldn't download: ${msg.take(120)}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
