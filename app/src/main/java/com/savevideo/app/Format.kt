package com.savevideo.app

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}
