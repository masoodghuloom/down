package com.savevideo.app

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale

/**
 * Turns a set of slideshow images plus an audio track (a TikTok / Instagram
 * "photo post") into a single MP4 that shows each image in turn over the full
 * sound, using ffmpeg.
 */
object SlideshowBuilder {

    /** @return true if [outFile] was produced successfully. */
    fun build(images: List<File>, audio: File?, outFile: File): Boolean {
        if (images.isEmpty()) return false
        val dir = outFile.parentFile ?: return false

        // Spread the whole audio across the images (one image => full song length).
        val audioDuration = audio?.let { probeDurationSeconds(it) } ?: 0.0
        val perImage =
            if (audioDuration > 0.5) (audioDuration / images.size).coerceAtLeast(0.5) else 3.0

        // Build a concat-demuxer list. The last image is repeated so its
        // duration is honoured (a quirk of the concat demuxer).
        val listFile = File(dir, "slides.txt")
        val sb = StringBuilder()
        for (img in images) {
            sb.append("file '").append(img.absolutePath).append("'\n")
            sb.append("duration ").append(String.format(Locale.US, "%.3f", perImage)).append('\n')
        }
        sb.append("file '").append(images.last().absolutePath).append("'\n")
        listFile.writeText(sb.toString())

        // Scale/pad every image to a common portrait canvas so mixed sizes line up.
        val vf = "scale=1080:1920:force_original_aspect_ratio=decrease," +
            "pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p"

        val cmd = buildString {
            append("-y -f concat -safe 0 -i ").append(quote(listFile.absolutePath))
            if (audio != null) append(" -i ").append(quote(audio.absolutePath))
            append(" -vf \"").append(vf).append("\"")
            append(" -r 30 -c:v mpeg4 -q:v 4")
            if (audio != null) append(" -c:a aac -b:a 192k -shortest")
            append(" -movflags +faststart ").append(quote(outFile.absolutePath))
        }

        val session = FFmpegKit.execute(cmd)
        return ReturnCode.isSuccess(session.returnCode) && outFile.exists() && outFile.length() > 0
    }

    private fun probeDurationSeconds(file: File): Double = try {
        FFprobeKit.getMediaInformation(file.absolutePath)
            .mediaInformation?.duration?.toDoubleOrNull() ?: 0.0
    } catch (e: Throwable) {
        0.0
    }

    private fun quote(path: String) = "\"" + path + "\""
}
