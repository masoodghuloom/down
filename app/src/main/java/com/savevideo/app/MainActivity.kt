package com.savevideo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Simple home screen: explains how to use the app and offers a box to paste a
 * link manually. The main way to use the app is the Share sheet, though.
 */
class MainActivity : AppCompatActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        askForNotifications()

        val input = findViewById<EditText>(R.id.urlInput)
        val status = findViewById<TextView>(R.id.status)

        findViewById<Button>(R.id.downloadBtn).setOnClickListener {
            val url = Urls.extract(input.text?.toString())
            if (url == null) {
                status.text = "Paste a TikTok, Instagram or YouTube link first."
                return@setOnClickListener
            }
            val service = Intent(this, DownloadService::class.java)
                .putExtra(DownloadService.EXTRA_URL, url)
            ContextCompat.startForegroundService(this, service)
            status.text = "Saving… watch the notification for progress."
            Toast.makeText(this, "Saving video…", Toast.LENGTH_SHORT).show()
            input.text?.clear()
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
