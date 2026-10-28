package com.savevideo.app

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Tiny HTTP helper for fetching JSON and downloading files. */
object Http {
    private const val UA = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun getString(url: String): String? = try {
        val conn = open(url)
        if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else null
    } catch (e: Throwable) {
        null
    }

    fun download(url: String, dest: File): Boolean = try {
        open(url).inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.length() > 0
    } catch (e: Throwable) {
        dest.delete()
        false
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20000
            readTimeout = 40000
            setRequestProperty("User-Agent", UA)
            connect()
        }
}
