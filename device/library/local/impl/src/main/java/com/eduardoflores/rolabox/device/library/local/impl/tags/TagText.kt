@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.local.impl.tags

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset

private val CP1252: Charset = Charset.forName("windows-1252")
private const val REPLACEMENT = "�"
private val BYTE_ORDER_MARK = Char(0xFEFF)
private const val NULL = '\u0000'
private const val FIRST_NON_ASCII = 0x80

/**
 * Text a tag declares as Latin-1, or that has no declaration, as ID3v1. Latin-1's 0x80 to 0x9F are control characters
 * no tag means, and Windows taggers wrote CP1252 there, so it is read as CP1252. Bytes past ASCII that are valid UTF-8
 * can't be there by chance, so they are read as UTF-8. Other code pages aren't guessed (ADR-024).
 */
internal fun ByteArray.decodeLegacy(): String {
    val utf8 = if (any { (it.toInt() and 0xFF) >= FIRST_NON_ASCII }) strictUtf8() else null
    return utf8 ?: toString(CP1252).replace(REPLACEMENT, "")
}

private fun ByteArray.strictUtf8(): String? = try {
    Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(this)).toString()
} catch (_: CharacterCodingException) {
    null
}

/**
 * UTF-16 as a frame declares it. Its byte order mark says the order. With none, the side the zeros of the mostly
 * ASCII text fall on says it, and little-endian, as Windows taggers wrote it, when that says nothing.
 */
internal fun ByteArray.decodeUtf16(): String {
    val hasMark = size >= 2
    val zerosFirst = indices.count { it % 2 == 0 && this[it] == 0.toByte() }
    val zerosSecond = indices.count { it % 2 == 1 && this[it] == 0.toByte() }
    return when {
        hasMark && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte() -> decodeUtf16Be()
        hasMark && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte() -> toString(Charsets.UTF_16LE)
        zerosFirst > zerosSecond -> decodeUtf16Be()
        else -> toString(Charsets.UTF_16LE)
    }
}

internal fun ByteArray.decodeUtf16Be(): String = toString(Charsets.UTF_16BE)

/** The first value of [this] text, with no stray whitespace or nulls, or null when nothing is left. */
internal fun String.cleanTagText(): String? = trim { it.isTagPadding() }
    .substringBefore(NULL)
    .trim { it.isTagPadding() }
    .takeIf { it.isNotEmpty() }

private fun Char.isTagPadding() = this == NULL || this == BYTE_ORDER_MARK || isWhitespace()

/** The number before any `/total`, when it is a positive number. "0" is what taggers write for no number. */
internal fun String.tagNumber(): Int? = substringBefore('/').trim().toIntOrNull()?.takeIf { it > 0 }

/** The year at the start of a date such as `2019` or `2019-05-01`. */
internal fun String.tagYear(): Int? = trim().take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
    ?.toInt()
    ?.takeIf { it > 0 }

/** A flag as ID3 writes it: "1" or "0". Anything else says nothing. */
internal fun String.tagFlag(): Boolean? = when (trim()) {
    "1" -> true
    "0" -> false
    else -> null
}
