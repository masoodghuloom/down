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
 * Foreground service that saves a shared link to the gallery. It first tries a
 * normal video download; if that yields nothing (a TikTok / Instagram photo
 * "slideshow" post), it downloads the images + music and stitches them into one
 * MP4 with the full sound.
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

        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov")
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
                val file = produceVideo(url, notifId)
                progress(notifId, "Saving to gallery…", -1)
                saveToGallery(file)
                file.parentFile?.deleteRecursively()
                done(notifId, "Saved to your gallery ✓")
            } catch (e: Throwable) {
                done(notifId, friendlyError(e))
            } finally {
                active.decrementAndGet()
                finishIfIdle(startId)
            }
        }
        return START_NOT_STICKY
    }

    // ----- Download orchestration ------------------------------------------

    /** Returns a playable video file, from either a normal post or a photo post. */
    private fun produceVideo(url: String, notifId: Int): File {
        val dir = File(cacheDir, "dl/$notifId").apply { deleteRecursively(); mkdirs() }

        // TikTok (video or photo) goes through the tikwm resolver, because the
        // yt-dlp engine can't handle TikTok reliably any more.
        if (TikTokApi.isTikTok(url)) {
            try {
                return downloadTikTok(url, dir, notifId)
            } catch (_: Throwable) { }
        }

        // 1) Normal video (Reel, YouTube, Shorts…)
        try {
            return downloadVideo(url, dir, notifId)
        } catch (_: Throwable) { }

        // 2) Photo / slideshow post -> build a video from images + music
        try {
            return buildFromPhotoPost(url, dir, notifId)
        } catch (_: Throwable) { }

        // 3) Engine may be stale (sites change); refresh once and retry both
        try {
            progress(notifId, "Updating downloader…", -1)
            YoutubeDL.getInstance().updateYoutubeDL(this)
        } catch (_: Throwable) { }

        try {
            return downloadVideo(url, dir, notifId)
        } catch (_: Throwable) { }

        return buildFromPhotoPost(url, dir, notifId) // last attempt; let it throw
    }

    /** Resolve and download a TikTok link (video or photo post) via tikwm. */
    private fun downloadTikTok(url: String, dir: File, notifId: Int): File {
        progress(notifId, "Fetching from TikTok…", -1)
        val media = TikTokApi.fetch(url) ?: throw IllegalStateException("TikTok lookup failed")

        // Photo / slideshow post -> images + music stitched into a video
        if (media.images.isNotEmpty()) {
            progress(notifId, "Downloading ${media.images.size} image(s) & sound…", -1)
            val images = mutableListOf<File>()
            media.images.forEachIndexed { i, imgUrl ->
                val f = File(dir, "img_%03d.jpg".format(i + 1))
                if (Http.download(imgUrl, f)) images.add(f)
            }
            if (images.isEmpty()) throw IllegalStateException("Could not download the images")

            val audio = media.audio?.let { a ->
                File(dir, "audio.m4a").takeIf { Http.download(a, it) }
            }

            progress(notifId, "Building video with sound…", -1)
            val out = File(dir, "slideshow.mp4")
            if (!SlideshowBuilder.build(images, audio, out)) {
                throw IllegalStateException("Could not build the slideshow video")
            }
            return out
        }

        // Normal video (no watermark)
        val videoUrl = media.video ?: throw IllegalStateException("No media found in this post")
        progress(notifId, "Downloading video…", -1)
        val out = File(dir, "video.mp4")
        if (!Http.download(videoUrl, out)) throw IllegalStateException("Could not download the video")
        return out
    }

    private fun downloadVideo(url: String, dir: File, notifId: Int): File {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--restrict-filenames")
            addOption("-f", "bv*+ba/b")
            addOption("--merge-output-format", "mp4")
            addOption("-o", File(dir, "%(title).60s.%(ext)s").absolutePath)
        }

        progress(notifId, "Fetching video…", 0)
        YoutubeDL.getInstance().execute(request) { pct, _, _ ->
            progress(notifId, "Downloading… ${pct.toInt().coerceIn(0, 100)}%", pct.toInt())
        }

        return dir.listFiles()
            ?.firstOrNull { it.isFile && it.extension.lowercase() in VIDEO_EXT }
            ?: throw IllegalStateException("No video stream")
    }

    private fun buildFromPhotoPost(url: String, dir: File, notifId: Int): File {
        progress(notifId, "Photo post — getting images & sound…", -1)

        // Slideshow images (each slide is a playlist entry)
        runQuietly {
            val req = YoutubeDLRequest(url).apply {
                addOption("--yes-playlist")
                addOption("--no-warnings")
                addOption("-o", File(dir, "img_%(autonumber)03d.%(ext)s").absolutePath)
            }
            YoutubeDL.getInstance().execute(req)
        }

        // Cover image fallback (covers the "single image + sound" case)
        runQuietly {
            val req = YoutubeDLRequest(url).apply {
                addOption("--skip-download")
                addOption("--write-thumbnail")
                addOption("--no-warnings")
                addOption("--convert-thumbnails", "jpg")
                addOption("-o", File(dir, "cover").absolutePath)
            }
            YoutubeDL.getInstance().execute(req)
        }

        // The background music
        runQuietly {
            val req = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("-f", "bestaudio/audio/ba")
                addOption("-o", File(dir, "audio.%(ext)s").absolutePath)
            }
            YoutubeDL.getInstance().execute(req)
        }

        val all = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        var images = all.filter { it.name.startsWith("img_") && it.extension.lowercase() in IMAGE_EXT }
            .sortedBy { it.name }
        if (images.isEmpty()) {
            images = all.filter { it.name.startsWith("cover") && it.extension.lowercase() in IMAGE_EXT }
        }
        if (images.isEmpty()) throw IllegalStateException("No images found in this post")

        val audio = all.firstOrNull {
            it.name.startsWith("audio.") && it.extension.lowercase() !in IMAGE_EXT
        }

        progress(notifId, "Building video with sound…", -1)
        val out = File(dir, "slideshow.mp4")
        if (!SlideshowBuilder.build(images, audio, out)) {
            throw IllegalStateException("Could not build the slideshow video")
        }
        return out
    }

    private inline fun runQuietly(block: () -> Unit) = try {
        block()
    } catch (_: Throwable) { }

    // ----- Engine init ------------------------------------------------------

    private fun ensureEngineReady() {
        synchronized(initLock) {
            if (!initialized) {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                initialized = true
            }
        }
    }

    // ----- Save to the gallery ---------------------------------------------

    private fun saveToGallery(file: File) {
        val name = ensureMp4Name(file.name)
        val mime = "video/mp4"

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
            val outDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SaveVideo"
            ).apply { mkdirs() }
            val dest = File(outDir, name)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(this, arrayOf(dest.absolutePath), arrayOf(mime), null)
        }
    }

    private fun ensureMp4Name(name: String): String =
        if (name.substringAfterLast('.', "").lowercase() in VIDEO_EXT) name
        else name.substringBeforeLast('.', name) + ".mp4"

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

    private fun done(id: Int, text: String) {
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
                "Couldn't download — this post may be private or need a login."
            msg.contains("Unsupported URL", true) -> "That link isn't supported."
            else -> "Couldn't download: ${msg.take(120)}"
        }
    }
}
