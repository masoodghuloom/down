package com.savevideo.app

import android.app.Application

/**
 * Application class. The heavy yt-dlp / ffmpeg initialisation is done lazily
 * inside [DownloadService] on a background thread, so app startup stays instant.
 */
class App : Application()
