package com.eduardoflores.rolabox.device.playback.ui.impl

import kotlin.time.Duration

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val TWO_DIGITS = 10L

/** A time as the display shows it: `1:12`, or `1:02:03` from an hour. A negative time is 0:00. */
internal fun Duration.toClock(): String {
    val total = inWholeSeconds.coerceAtLeast(0L)
    val seconds = total % SECONDS_PER_MINUTE
    val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val hours = total / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val secondsText = seconds.padded()
    return if (hours > 0) "$hours:${minutes.padded()}:$secondsText" else "$minutes:$secondsText"
}

private fun Long.padded() = if (this < TWO_DIGITS) "0$this" else "$this"

/** The time left in the song, with a `-` before it: `-2:29`. */
internal fun timeLeftClock(position: Duration, duration: Duration): String = "-" + (duration - position).toClock()
