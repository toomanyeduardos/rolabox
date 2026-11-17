package com.eduardoflores.rolabox.device.podcasts.network.impl

import androidx.core.text.HtmlCompat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SECONDS_PER_MINUTE = 60

/** What an image leaves in the text HtmlCompat makes: the object replacement character. */
private const val OBJECT_REPLACEMENT = '￼'
private const val NO_BREAK_SPACE = ' '
private val SpacesAtLineEnds = Regex("[ \\t]*\\n[ \\t]*")
private val RunsOfSpaces = Regex("[ \\t]{2,}")
private val ThreeOrMoreNewlines = Regex("\\n{3,}")

/**
 * [html] as plain text, with paragraph breaks as blank lines (ADR-031, Feeds). Links keep their text and lose their
 * address, and images are dropped: the display can't open either. `null` when nothing is left.
 */
internal fun plainTextOf(html: String): String? {
    val text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        .replace(OBJECT_REPLACEMENT, ' ')
        .replace(NO_BREAK_SPACE, ' ')
        .replace(SpacesAtLineEnds, "\n")
        .replace(RunsOfSpaces, " ")
        .replace(ThreeOrMoreNewlines, "\n\n")
        .trim()
    return text.ifEmpty { null }
}

/**
 * An `itunes:duration`: seconds, `MM:SS` or `HH:MM:SS`, with or without a fraction of a second. `null` when it is
 * none of them, or zero, which the player then learns.
 */
internal fun durationOf(text: String): Duration? {
    val parts = text.trim().split(':').map { it.trim().toDoubleOrNull()?.takeIf { value -> value >= 0 } }
    if (parts.size > MAX_DURATION_PARTS || parts.any { it == null }) return null
    // Hours, minutes and seconds, each worth 60 of the next.
    val total = parts.filterNotNull().fold(0.0) { sum, part -> sum * SECONDS_PER_MINUTE + part }
    return total.takeIf { it > 0 }?.seconds
}

/** Hours, minutes and seconds. */
private const val MAX_DURATION_PARTS = 3

private val LeadingWeekday = Regex("^[A-Za-z]{2,9},?\\s+")
private val Whitespace = Regex("\\s+")

// RFC 822's zone names, which java.time doesn't read, and that feeds still write.
private val ZoneNames = mapOf(
    "UT" to "+0000",
    "UTC" to "+0000",
    "GMT" to "+0000",
    "Z" to "+0000",
    "EST" to "-0500",
    "EDT" to "-0400",
    "CST" to "-0600",
    "CDT" to "-0500",
    "MST" to "-0700",
    "MDT" to "-0600",
    "PST" to "-0800",
    "PDT" to "-0700",
)

// "9 Dec 2025 16:27:41 +1100", seconds optional, the offset with or without its colon, the month in any case.
private val Rfc822 = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("d MMM yyyy HH:mm[:ss] [xxx][xx]")
    .toFormatter(Locale.ENGLISH)

/**
 * A `pubDate`, as RFC 822 writes it and as feeds write it anyway: a weekday that is wrong or missing, a zone by name,
 * no seconds. ISO 8601 is read too. `null` when it can't be read: the episode is listed with no date.
 */
internal fun instantOf(text: String): Instant? {
    val words = text.trim().replace(LeadingWeekday, "").split(Whitespace).toMutableList()
    if (words.isEmpty()) return null
    ZoneNames[words.last().uppercase(Locale.ROOT)]?.let { words[words.lastIndex] = it }
    return parseOrNull { ZonedDateTime.parse(words.joinToString(" "), Rfc822).toInstant() }
        ?: parseOrNull { OffsetDateTime.parse(text.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }
}

private inline fun parseOrNull(parse: () -> Instant): Instant? = try {
    parse()
} catch (_: DateTimeParseException) {
    null
}
