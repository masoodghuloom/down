package com.savevideo.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Draw a box over a frame and crop the video to it. */
class CropActivity : AppCompatActivity() {

    private lateinit var videoUri: Uri
    private lateinit var image: ImageView
    private lateinit var overlay: CropOverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        videoUri = Uri.parse(intent.getStringExtra(VideoDetailActivity.EXTRA_URI))

        image = findViewById(R.id.frame)
        overlay = findViewById(R.id.overlay)

        val ctx = this
        setBusy(true)
        Bg.run({ loadFrame(ctx, videoUri) }) { bmp ->
            setBusy(false)
            if (bmp != null) image.setImageBitmap(bmp)
            else toast("Couldn't load a frame")
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun loadFrame(ctx: android.content.Context, uri: Uri): Bitmap? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(ctx, uri)
        val frame = retriever.getFrameAtTime(0)
        retriever.release()
        frame
    } catch (e: Throwable) {
        null
    }

    private fun save() {
        val drawable = image.drawable
        if (drawable == null || !overlay.hasSelection()) {
            toast("Draw a box over the area to keep")
            return
        }

        // Map the overlay rectangle (view pixels) to video pixels. The ImageView
        // uses fitCenter, so compute that transform by hand (imageMatrix is identity).
        val viewW = image.width.toFloat()
        val viewH = image.height.toFloat()
        val bmpW = drawable.intrinsicWidth
        val bmpH = drawable.intrinsicHeight
        if (viewW <= 0f || viewH <= 0f || bmpW <= 0 || bmpH <= 0) {
            toast("Not ready yet — try again")
            return
        }
        val scale = min(viewW / bmpW, viewH / bmpH)
        val transX = (viewW - bmpW * scale) / 2f
        val transY = (viewH - bmpH * scale) / 2f

        val sel = overlay.selection()
        var x = ((sel.left - transX) / scale).roundToInt().coerceIn(0, bmpW - 2)
        var y = ((sel.top - transY) / scale).roundToInt().coerceIn(0, bmpH - 2)
        var w = (sel.width() / scale).roundToInt().coerceAtLeast(2)
        var h = (sel.height() / scale).roundToInt().coerceAtLeast(2)
        if (x + w > bmpW) w = bmpW - x
        if (y + h > bmpH) h = bmpH - y
        // mpeg4 / yuv420p need even dimensions.
        w -= w % 2; h -= h % 2; x -= x % 2; y -= y % 2
        if (w < 2 || h < 2) {
            toast("Crop area is too small")
            return
        }
        val cx = x; val cy = y; val cw = w; val ch = h

        setBusy(true)
        val ctx = this
        val uri = videoUri
        Bg.run({
            try {
                val input = Gallery.copyToCache(ctx, uri, "crop_in.mp4")
                val out = File(ctx.cacheDir, "crop_out.mp4")
                if (FfmpegOps.crop(input, cx, cy, cw, ch, out)) Gallery.save(ctx, out, "crop") else null
            } catch (e: Throwable) {
                null
            }
        }) { result ->
            setBusy(false)
            if (result != null) {
                toast("Saved cropped video ✓")
                finish()
            } else {
                toast("Crop failed")
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        findViewById<View>(R.id.busy).visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
