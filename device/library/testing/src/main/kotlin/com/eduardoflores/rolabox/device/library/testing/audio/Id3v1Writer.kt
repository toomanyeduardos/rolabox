@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

/** Writes an ID3v1 tag, the 128 bytes at the end of an MP3, and ID3v1.1 when there's a track number. */
internal object Id3v1Writer {
    private const val LENGTH = 128
    private const val FIELD = 30
    private const val COMMENT_WITH_TRACK = 28
    private const val UNKNOWN_GENRE = 255

    // The start of ID3v1's genre list: enough for the fixtures' genres.
    private val GENRES = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
        "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
    )

    fun write(tags: Tags): ByteArray {
        val track = tags.track?.substringBefore('/')?.toIntOrNull()?.takeIf { it in 1..255 }
        return bytes {
            ascii("TAG")
            append(field(tags.title, FIELD))
            append(field(tags.artist, FIELD))
            append(field(tags.album, FIELD))
            append(field(tags.year, 4))
            if (track != null) {
                // ID3v1.1: the comment's last two bytes are a zero and the track.
                zeros(COMMENT_WITH_TRACK + 1)
                u8(track)
            } else {
                zeros(FIELD)
            }
            u8(genre(tags.genre))
        }.also { check(it.size == LENGTH) }
    }

    // Latin-1, cut or padded with zeros to the field's length.
    private fun field(value: String?, length: Int): ByteArray =
        value.orEmpty().toByteArray(Charsets.ISO_8859_1).copyOf(length)

    // One genre byte: "(17)" and "17" are written as 17, and a name by its number in the list.
    private fun genre(genre: String?): Int {
        val first = genre?.substringBefore('\u0000') ?: return UNKNOWN_GENRE
        val number = first.removeSurrounding("(", ")").toIntOrNull()
        return number ?: GENRES.indexOf(first).takeIf { it >= 0 } ?: UNKNOWN_GENRE
    }
}
