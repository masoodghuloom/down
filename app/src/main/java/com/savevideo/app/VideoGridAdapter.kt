package com.savevideo.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

/** Grid adapter for the downloads library, with lazy thumbnail loading. */
class VideoGridAdapter(private val context: Context) : BaseAdapter() {

    private var items: List<Video> = emptyList()
    private val cache = object : LruCache<Long, Bitmap>(60) {}

    fun setItems(list: List<Video>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int): Video = items[position]
    override fun getItemId(position: Int) = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
        val video = items[position]

        val thumb = view.findViewById<ImageView>(R.id.thumb)
        val duration = view.findViewById<TextView>(R.id.duration)
        duration.text = formatDuration(video.durationMs)

        thumb.tag = video.id
        val cached = cache.get(video.id)
        if (cached != null) {
            thumb.setImageBitmap(cached)
        } else {
            thumb.setImageDrawable(null)
            Bg.run({ loadThumb(video) }) { bmp ->
                if (bmp != null) {
                    cache.put(video.id, bmp)
                    if (thumb.tag == video.id) thumb.setImageBitmap(bmp)
                }
            }
        }
        return view
    }

    private fun loadThumb(video: Video): Bitmap? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, video.uri)
        val frame = retriever.getFrameAtTime(0)
        retriever.release()
        if (frame == null) null else {
            // Downscale to a thumbnail so the cache stays small.
            val target = 360f
            val scale = minOf(target / frame.width, target / frame.height).coerceAtMost(1f)
            if (scale >= 1f) frame else {
                val w = (frame.width * scale).toInt().coerceAtLeast(1)
                val h = (frame.height * scale).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(frame, w, h, true)
                if (small != frame) frame.recycle()
                small
            }
        }
    } catch (e: Throwable) {
        null
    }
}
