package com.sereneflow.timer

import com.sereneflow.timer.platform.Feedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackTest {
    @Test
    fun keepsUnreservedCharacters() {
        assertEquals("AZaz09-._~", Feedback.percentEncode("AZaz09-._~"))
    }

    @Test
    fun encodesSpacesNewlinesAndQueryDelimiters() {
        assertEquals("a%20b%0Ac%26d%3De%3Ff%23g%2Bh", Feedback.percentEncode("a b\nc&d=e?f#g+h"))
    }

    @Test
    fun encodesNonAsciiAsUtf8Bytes() {
        // The em dash in the body, and letters outside ASCII that isLetterOrDigit would let through.
        assertEquals("%E2%80%94", Feedback.percentEncode("—"))
        assertEquals("%C3%A9%D0%96", Feedback.percentEncode("éЖ"))
        assertEquals("%F0%9F%98%80", Feedback.percentEncode("😀"))
    }

    @Test
    fun emailIsOneWellFormedAddress() {
        assertTrue(Regex("[a-z]+@[a-z]+\\.[a-z]+").matches(Feedback.EMAIL), Feedback.EMAIL)
    }

    @Test
    fun mailtoHasOneQueryWithEncodedFields() {
        val url = Feedback.mailtoUrl(subject = "Hi & bye", body = "x=1\ny")
        assertTrue(url.startsWith("mailto:${Feedback.EMAIL}?"))
        assertEquals("subject=Hi%20%26%20bye&body=x%3D1%0Ay", url.substringAfter('?'))
    }
}
