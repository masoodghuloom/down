package com.savevideo.app

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale

/** ffmpeg-backed edits. Each returns true if [out] was produced. */
object FfmpegOps {

    fun trim(input: File, startSec: Double, endSec: Double, out: File): Boolean {
        val duration = (endSec - startSec).coerceAtLeast(0.1)
        // Stream copy = fast and lossless (cut starts at the nearest keyframe).
        val cmd = "-y -ss ${sec(startSec)} -i ${q(input)} -t ${sec(duration)} " +
            "-c copy -movflags +faststart ${q(out)}"
        return run(cmd, out)
    }

    fun rotate(input: File, degrees: Int, out: File): Boolean {
        val vf = when (((degrees % 360) + 360) % 360) {
            90 -> "transpose=1"
            180 -> "transpose=2,transpose=2"
            270 -> "transpose=2"
            else -> return false
        }
        val cmd = "-y -i ${q(input)} -vf \"$vf,format=yuv420p\" " +
            "-c:v libx264 -preset veryfast -crf 23 -c:a copy -movflags +faststart ${q(out)}"
        return run(cmd, out)
    }

    fun crop(input: File, x: Int, y: Int, w: Int, h: Int, out: File): Boolean {
        val cmd = "-y -i ${q(input)} -vf \"crop=$w:$h:$x:$y,format=yuv420p\" " +
            "-c:v libx264 -preset veryfast -crf 23 -c:a copy -movflags +faststart ${q(out)}"
        return run(cmd, out)
    }

    private fun run(cmd: String, out: File): Boolean = try {
        val session = FFmpegKit.execute(cmd)
        ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0
    } catch (e: Throwable) {
        false
    }

    private fun q(f: File) = "\"" + f.absolutePath + "\""
    private fun sec(v: Double) = String.format(Locale.US, "%.3f", v)
}
