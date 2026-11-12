package com.savevideo.app

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Minimal background-work helper: run [work] off the UI thread, deliver [done] on it. */
object Bg {
    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (T) -> Unit) {
        pool.execute {
            val result = work()
            main.post { done(result) }
        }
    }
}
