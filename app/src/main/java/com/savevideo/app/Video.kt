package com.savevideo.app

import android.net.Uri

/** A downloaded video shown in the library. */
data class Video(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
)
