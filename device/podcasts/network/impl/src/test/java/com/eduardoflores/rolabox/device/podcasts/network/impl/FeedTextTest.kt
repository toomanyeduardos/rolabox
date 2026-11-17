package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** How a feed's lengths, dates and descriptions are read. HtmlCompat is the platform's, so this runs on Robolectric. */
@RunWith(RobolectricTestRunner::class)
class FeedTextTest {
    @Test
    fun aDuration_isSecondsMinutesOrHours() {
        assertEquals(3146.seconds, durationOf("3146"))
        assertEquals(2.minutes + 5.seconds, durationOf("02:05"))
        assertEquals(1.hours + 2.minutes + 3.seconds, durationOf(" 1:02:03 "))
        assertEquals(90.seconds + 500.milliseconds, durationOf("90.5"))
    }

    @Test
    fun aDurationThatIsntOne_isNone() {
        listOf("", "soon", "1:2:3:4", "1::2", "-5", "0", "00:00").forEach { assertNull(it, durationOf(it)) }
    }

    @Test
    fun aDate_isReadAsFeedsWriteIt() {
        val expected = Instant.parse("2025-12-09T05:27:41Z")
        assertEquals(expected, instantOf("Tue, 09 Dec 2025 16:27:41 +1100"))
        assertEquals(expected, instantOf("Tue, 9 Dec 2025 16:27:41 +11:00"))
        // A wrong weekday, none, a zone by name, no seconds, a month in capitals.
        assertEquals(expected, instantOf("Mon, 09 Dec 2025 05:27:41 GMT"))
        assertEquals(expected, instantOf("09 DEC 2025 05:27:41 UTC"))
        assertEquals(Instant.parse("2025-12-09T05:27:00Z"), instantOf("Tuesday, 9 Dec 2025 00:27 EST"))
        assertEquals(expected, instantOf("2025-12-09T16:27:41+11:00"))
    }

    @Test
    fun aDateThatIsntOne_isNone() {
        listOf("", "yesterday", "09 Dec 2025", "09 Dec 2025 16:27:41").forEach { assertNull(it, instantOf(it)) }
    }

    @Test
    fun html_isPlainText_withParagraphsAsBlankLines() {
        assertEquals(
            "One & two.\n\nThree\nfour, with a link.",
            plainTextOf("<p>One &amp; two.</p>\n\n<p>Three<br/>  four, with <a href=\"https://e.com\">a link</a>.</p>"),
        )
    }

    @Test
    fun imagesAreDropped_andNothingLeft_isNone() {
        assertEquals("Before after", plainTextOf("Before <img src=\"https://e.com/i.png\"/>after"))
        assertNull(plainTextOf("<p> <img src=\"https://e.com/i.png\"/> </p>"))
    }
}
