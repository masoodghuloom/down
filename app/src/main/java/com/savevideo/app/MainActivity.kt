package com.savevideo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Home screen: paste-a-link box plus a grid of everything you've downloaded.
 * Tap a video to open its detail / edit screen.
 */
class MainActivity : AppCompatActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private lateinit var adapter: VideoGridAdapter

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
            status.text = "Saving… it'll appear below when done."
            Toast.makeText(this, "Saving video…", Toast.LENGTH_SHORT).show()
            input.text?.clear()
        }

        adapter = VideoGridAdapter(this)
        val grid = findViewById<GridView>(R.id.grid)
        grid.adapter = adapter
        grid.emptyView = findViewById(R.id.empty)
        grid.setOnItemClickListener { _, _, position, _ ->
            val video = adapter.getItem(position)
            startActivity(
                Intent(this, VideoDetailActivity::class.java)
                    .putExtra(VideoDetailActivity.EXTRA_URI, video.uri.toString())
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val ctx = this
        Bg.run({ Gallery.list(ctx) }) { adapter.setItems(it) }
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
