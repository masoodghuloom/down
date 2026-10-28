package com.savevideo.app

import org.json.JSONObject
import java.net.URLEncoder

/**
 * TikTok resolver. yt-dlp can no longer handle TikTok reliably (videos need
 * browser impersonation it lacks, and photo/slideshow posts aren't supported at
 * all), so TikTok links are resolved through the tikwm API, which returns a
 * no-watermark video URL for videos and the image + music URLs for photo posts.
 */
object TikTokApi {

    data class Media(val images: List<String>, val audio: String?, val video: String?)

    fun isTikTok(url: String): Boolean = url.contains("tiktok.com", ignoreCase = true)

    /** @return resolved media, or null if the service didn't return a usable result. */
    fun fetch(url: String): Media? {
        // One quick retry — the free API occasionally rate-limits.
        return query(url) ?: run { Thread.sleep(1500); query(url) }
    }

    private fun query(url: String): Media? {
        val api = "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(url, "UTF-8")
        val body = Http.getString(api) ?: return null
        val root = try { JSONObject(body) } catch (e: Throwable) { return null }
        if (root.optInt("code", -1) != 0) return null
        val data = root.optJSONObject("data") ?: return null

        val images = mutableListOf<String>()
        data.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) images.add(absolute(s))
            }
        }

        var audio = data.optString("music", "")
        if (audio.isBlank()) audio = data.optJSONObject("music_info")?.optString("play").orEmpty()
        val video = data.optString("play", "")

        return Media(
            images = images,
            audio = audio.ifBlank { null }?.let { absolute(it) },
            video = video.ifBlank { null }?.let { absolute(it) },
        )
    }

    private fun absolute(u: String) = if (u.startsWith("/")) "https://www.tikwm.com$u" else u
}
