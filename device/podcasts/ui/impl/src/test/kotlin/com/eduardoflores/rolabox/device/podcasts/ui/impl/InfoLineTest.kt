package com.eduardoflores.rolabox.device.podcasts.ui.impl

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfoLineTest {
    private val words = EpisodeWords(
        play = "Play",
        resume = "Resume",
        playFromStart = "Play from Start",
        needsWifi = "Needs Wi-Fi. Select to allow mobile data.",
        dateThisYear = "MMM d",
        dateOtherYear = "MMM d, yyyy",
        minutes = "%d MIN",
        hours = "%d HR",
        hoursMinutes = "%1\$d HR %2\$d MIN",
        underAMinute = "<1 MIN",
        timeLeft = "%s LEFT",
    )
    private val today = LocalDate.of(2026, 11, 17)
    private val nov8 = Instant.parse("2026-11-08T15:00:00Z")

    private fun line(info: EpisodeInfo, zone: ZoneId = ZoneOffset.UTC) = infoLine(info, words, today, zone, Locale.US)

    @Test
    fun partlyPlayed_isTheDateTheLengthAndTheTimeLeft() {
        assertEquals(
            "NOV 8 · 32 MIN · 20 MIN LEFT",
            line(EpisodeInfo(nov8, 32.minutes, left = 20.minutes + 31.seconds)),
        )
    }

    @Test
    fun withNoTimeLeft_isTheDateAndTheLength() {
        assertEquals("NOV 8 · 32 MIN", line(EpisodeInfo(nov8, 32.minutes, left = null)))
    }

    @Test
    fun dateOfAnotherYear_hasItsYear() {
        assertEquals(
            "MAR 2, 2025 · 32 MIN",
            line(EpisodeInfo(Instant.parse("2025-03-02T12:00:00Z"), 32.minutes, left = null)),
        )
    }

    @Test
    fun date_isThePhonesDay() {
        val lateOnNov8InDenver = Instant.parse("2026-11-09T03:00:00Z")

        assertEquals("NOV 8", line(EpisodeInfo(lateOnNov8InDenver, null, null), ZoneId.of("America/Denver")))
    }

    @Test
    fun anHourOrMore_isInHoursAndMinutes() {
        assertEquals("NOV 8 · 1 HR 12 MIN", line(EpisodeInfo(nov8, 72.minutes, left = null)))
        assertEquals("NOV 8 · 2 HR · 1 HR 5 MIN LEFT", line(EpisodeInfo(nov8, 2.hours, left = 65.minutes)))
    }

    @Test
    fun underAMinuteLeft_saysSo() {
        assertEquals("NOV 8 · 32 MIN · <1 MIN LEFT", line(EpisodeInfo(nov8, 32.minutes, left = 40.seconds)))
    }

    @Test
    fun partsThatArentKnown_areLeftOut() {
        assertEquals("32 MIN", line(EpisodeInfo(null, 32.minutes, left = null)))
        assertEquals("NOV 8", line(EpisodeInfo(nov8, null, left = null)))
    }

    @Test
    fun nothingKnown_isNoLine() {
        assertNull(line(EpisodeInfo(null, null, null)))
    }
}
