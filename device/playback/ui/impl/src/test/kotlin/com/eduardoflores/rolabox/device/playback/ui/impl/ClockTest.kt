package com.eduardoflores.rolabox.device.playback.ui.impl

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test
    fun underAMinute_hasOneZeroMinute() = assertEquals("0:07", 7.seconds.toClock())

    @Test
    fun minutesAndSeconds_arePadded() = assertEquals("1:02", (1.minutes + 2.seconds).toClock())

    @Test
    fun fromAnHour_showsHours() = assertEquals("1:02:03", (1.hours + 2.minutes + 3.seconds).toClock())

    @Test
    fun partOfASecond_isDropped() = assertEquals("0:01", 1999.milliseconds.toClock())

    @Test
    fun aNegativeTime_isZero() = assertEquals("0:00", (-5).seconds.toClock())

    @Test
    fun timeLeft_hasAMinus() = assertEquals("-2:29", timeLeftClock(1.minutes + 12.seconds, 3.minutes + 41.seconds))

    @Test
    fun timeLeftAtTheEnd_isZero() = assertEquals("-0:00", timeLeftClock(3.minutes, 3.minutes))

    @Test
    fun timeLeftPastTheEnd_isZero() = assertEquals("-0:00", timeLeftClock(4.minutes, Duration.ZERO + 3.minutes))
}
