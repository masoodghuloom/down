package com.savevideo.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Preview a downloaded video and manage/edit it. */
class VideoDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "uri"
    }

    private lateinit var videoUri: Uri
    private lateinit var player: VideoView

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                toast("Deleted")
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detail)

        videoUri = Uri.parse(intent.getStringExtra(EXTRA_URI))

        player = findViewById(R.id.player)
        player.setVideoURI(videoUri)
        MediaController(this).also { it.setAnchorView(player); player.setMediaController(it) }
        player.setOnPreparedListener { it.isLooping = false }
        player.start()

        findViewById<Button>(R.id.btnShare).setOnClickListener { share() }
        findViewById<Button>(R.id.btnRename).setOnClickListener { rename() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.btnTrim).setOnClickListener { openEditor(TrimActivity::class.java) }
        findViewById<Button>(R.id.btnCrop).setOnClickListener { openEditor(CropActivity::class.java) }
        findViewById<Button>(R.id.btnRotateLeft).setOnClickListener { rotate(270) }
        findViewById<Button>(R.id.btnRotateRight).setOnClickListener { rotate(90) }
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    private fun updateTitle() {
        val ctx = this
        Bg.run({ Gallery.displayName(ctx, videoUri) }) { name ->
            findViewById<TextView>(R.id.title).text = name
        }
    }

    private fun baseName(): String =
        Gallery.displayName(this, videoUri).substringBeforeLast('.', "video")

    private fun share() {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Share video"))
    }

    private fun rename() {
        val input = EditText(this).apply { setText(baseName()) }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val ok = Gallery.rename(this, videoUri, input.text.toString())
                toast(if (ok) "Renamed" else "Rename failed")
                updateTitle()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete video?")
            .setMessage("This removes it from your gallery.")
            .setPositiveButton("Delete") { _, _ ->
                val sender = Gallery.deleteRequest(this, videoUri)
                if (sender != null) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    toast("Deleted")
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEditor(clazz: Class<*>) {
        startActivity(Intent(this, clazz).putExtra(EXTRA_URI, videoUri.toString()))
    }

    private fun rotate(degrees: Int) {
        setBusy(true)
        val ctx = this
        val uri = videoUri
        val suggested = baseName()
        Bg.run({
            try {
                val input = Gallery.copyToCache(ctx, uri, "rotate_in.mp4")
                val out = File(ctx.cacheDir, "rotate_out.mp4")
                if (FfmpegOps.rotate(input, degrees, out)) Gallery.save(ctx, out, suggested) else null
            } catch (e: Throwable) {
                null
            }
        }) { result ->
            setBusy(false)
            toast(if (result != null) "Saved rotated copy ✓" else "Rotate failed")
        }
    }

    private fun setBusy(busy: Boolean) {
        findViewById<android.view.View>(R.id.busy).visibility =
            if (busy) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
