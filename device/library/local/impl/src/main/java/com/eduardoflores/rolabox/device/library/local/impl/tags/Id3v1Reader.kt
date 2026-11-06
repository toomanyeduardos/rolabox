@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.local.impl.tags

import com.eduardoflores.rolabox.device.library.local.api.FileTags

private const val LENGTH = 128
private const val TEXT_FIELD = 30
private const val TITLE_AT = 3
private const val ARTIST_AT = 33
private const val ALBUM_AT = 63
private const val YEAR_AT = 93
private const val V11_ZERO_AT = 125
private const val V11_TRACK_AT = 126
private const val GENRE_AT = 127
private const val NULL = '\u0000'

/** ID3v1 and v1.1, the last 128 bytes of the file. Null when they aren't a tag. */
internal fun readId3v1(source: ByteSource): FileTags? {
    val tag = if (source.size < LENGTH) ByteArray(0) else source.read(source.size - LENGTH, LENGTH)
    return if (tag.size == LENGTH && tag.ascii(0, 3) == "TAG") tag.toFileTags() else null
}

private fun ByteArray.toFileTags() = FileTags(
    title = text(TITLE_AT, TEXT_FIELD),
    artist = text(ARTIST_AT, TEXT_FIELD),
    album = text(ALBUM_AT, TEXT_FIELD),
    year = text(YEAR_AT, 4)?.tagYear(),
    // In v1.1, a zero before the comment's last byte makes that byte the track.
    trackNumber = if (this[V11_ZERO_AT] == 0.toByte()) u8(V11_TRACK_AT).takeIf { it > 0 } else null,
    hasEmbeddedPicture = false,
    genres = listOfNotNull(id3v1Genre(u8(GENRE_AT))),
)

// A field ends at its first null, and what follows is whatever the tagger left there.
private fun ByteArray.text(at: Int, length: Int): String? =
    copyOfRange(at, at + length).decodeLegacy().substringBefore(NULL).cleanTagText()
