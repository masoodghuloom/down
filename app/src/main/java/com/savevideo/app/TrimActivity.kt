package com.savevideo.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Cut the start/end off a video and save the shorter clip. */
class TrimActivity : AppCompatActivity() {

    private lateinit var videoUri: Uri
    private lateinit var player: VideoView
    private lateinit var startBar: SeekBar
    private lateinit var endBar: SeekBar
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView

    private var durationSec = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trim)
        videoUri = Uri.parse(intent.getStringExtra(VideoDetailActivity.EXTRA_URI))

        player = findViewById(R.id.player)
        startBar = findViewById(R.id.startBar)
        endBar = findViewById(R.id.endBar)
        startLabel = findViewById(R.id.startLabel)
        endLabel = findViewById(R.id.endLabel)

        player.setVideoURI(videoUri)
        player.setOnPreparedListener { mp ->
            durationSec = (mp.duration / 1000).coerceAtLeast(1)
            startBar.max = durationSec
            endBar.max = durationSec
            endBar.progress = durationSec
            updateLabels()
            mp.seekTo(0)
        }
        player.start()

        startBar.setOnSeekBarChangeListener(barListener { value ->
            if (value >= endBar.progress) startBar.progress = (endBar.progress - 1).coerceAtLeast(0)
            player.seekTo(startBar.progress * 1000)
            updateLabels()
        })
        endBar.setOnSeekBarChangeListener(barListener { value ->
            if (value <= startBar.progress) endBar.progress = (startBar.progress + 1).coerceAtMost(durationSec)
            updateLabels()
        })

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun updateLabels() {
        startLabel.text = "Start:  ${formatDuration(startBar.progress * 1000L)}"
        endLabel.text = "End:  ${formatDuration(endBar.progress * 1000L)}"
    }

    private fun save() {
        val start = startBar.progress.toDouble()
        val end = endBar.progress.toDouble()
        if (end - start < 0.5) {
            toast("Make the clip a bit longer")
            return
        }
        setBusy(true)
        val ctx = this
        val uri = videoUri
        Bg.run({
            try {
                val input = Gallery.copyToCache(ctx, uri, "trim_in.mp4")
                val out = File(ctx.cacheDir, "trim_out.mp4")
                if (FfmpegOps.trim(input, start, end, out)) Gallery.save(ctx, out, "trim") else null
            } catch (e: Throwable) {
                null
            }
        }) { result ->
            setBusy(false)
            if (result != null) {
                toast("Saved trimmed clip ✓")
                finish()
            } else {
                toast("Trim failed")
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        findViewById<View>(R.id.busy).visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun barListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
