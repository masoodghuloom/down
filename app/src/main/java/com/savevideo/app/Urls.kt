package com.savevideo.app

/** Pulls the first http(s) link out of shared text (which often looks like
 *  "Check this out https://vt.tiktok.com/xxxx/ ..."). */
object Urls {
    private val REGEX = Regex("""https?://\S+""")

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
    }
}
