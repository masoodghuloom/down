package com.savevideo.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All gallery (MediaStore) access lives here: save, list, rename, delete. */
object Gallery {

    const val ALBUM = "SaveVideo"
    private val relPath get() = "${Environment.DIRECTORY_DCIM}/$ALBUM"
    private val collection: Uri get() = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** Copy a finished video file into the gallery (DCIM/SaveVideo). */
    fun save(context: Context, source: File, suggestedName: String? = null): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, uniqueName(suggestedName ?: source.name))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create gallery entry")
        resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** All videos this app saved, newest first. */
    fun list(context: Context): List<Video> {
        val out = ArrayList<Video>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%${Environment.DIRECTORY_DCIM}/$ALBUM%")
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeI = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(idI)
                out.add(
                    Video(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(nameI) ?: "video.mp4",
                        durationMs = c.getLong(durI),
                        sizeBytes = c.getLong(sizeI),
                    )
                )
            }
        }
        return out
    }

    fun rename(context: Context, uri: Uri, newBaseName: String): Boolean {
        val safe = newBaseName.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().take(60).ifBlank { "video" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$safe.mp4")
        }
        return try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * On Android 11+ returns an IntentSender the caller must launch to get the
     * user's confirmation; on 10 it deletes directly and returns null.
     */
    fun deleteRequest(context: Context, uri: Uri): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
        } else {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Throwable) { }
            null
        }
    }

    /** Copy a gallery video out to a cache file so ffmpeg can read it by path. */
    fun copyToCache(context: Context, uri: Uri, fileName: String): File {
        val dest = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest
    }

    /** The display name (e.g. "SaveVideo_….mp4") of a gallery video. */
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(
            uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) return it.getString(0) ?: "video.mp4" }
        return "video.mp4"
    }

    private fun uniqueName(original: String): String {
        val base = original.substringBeforeLast('.', original)
            .replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "video" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "SaveVideo_${stamp}_$base.mp4"
    }
}
