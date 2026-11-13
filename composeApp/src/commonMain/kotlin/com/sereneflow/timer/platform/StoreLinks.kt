package com.sereneflow.timer.platform

/**
 * The store page where the user writes a review: Google Play's listing (the Play Store app opens
 * it), or the App Store's write-review sheet.
 */
expect val rateAppUrl: String

/** The app's version and the OS it runs on, e.g. ("1.3", "Android", "16"), for feedback emails. */
expect fun appEnvironment(): AppEnvironment

data class AppEnvironment(val appVersion: String, val osName: String, val osVersion: String)

object Feedback {
    /**
     * Where "Send feedback" writes to: the contact email of the store listings. Put together at
     * run time so the address never appears whole in the source or the binary's strings, where
     * address-harvesting scrapers look for it.
     */
    val EMAIL: String by lazy {
        val user = listOf("daniel", "bellone", "word").joinToString("")
        val domain = listOf("gmail", "com").joinToString(".")
        listOf(user, domain).joinToString("@")
    }

    /** A `mailto:` link to [EMAIL] with [subject] and [body] filled in. */
    fun mailtoUrl(subject: String, body: String): String =
        "mailto:$EMAIL?subject=${percentEncode(subject)}&body=${percentEncode(body)}"

    /** RFC 3986 percent-encoding of UTF-8 bytes, keeping only the unreserved characters. */
    internal fun percentEncode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~")) append(ch)
            else append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
