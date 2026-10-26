package com.savevideo.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Invisible activity that is the target when you tap "Share" in TikTok /
 * Instagram / YouTube and pick this app. It grabs the link, kicks off the
 * download service, and immediately disappears so it feels automatic.
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = Urls.extract(shared)

        if (url == null) {
            Toast.makeText(this, "No video link found in what you shared.", Toast.LENGTH_LONG).show()
        } else {
            val service = Intent(this, DownloadService::class.java)
                .putExtra(DownloadService.EXTRA_URL, url)
            ContextCompat.startForegroundService(this, service)
            Toast.makeText(this, "Saving video… check your notifications.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
