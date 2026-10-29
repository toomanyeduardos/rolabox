@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

import java.nio.charset.Charset

/** Writes an ID3v2.3 or ID3v2.4 tag, as the specifications at id3.org lay them out. */
internal object Id3v2Writer {
    private const val PADDING = 256
    private const val PICTURE_FRONT_COVER = 3

    private val CP1252: Charset = Charset.forName("windows-1252")

    fun write(tag: Mp3Tags.Id3v2): ByteArray {
        val v24 = tag.version == Id3Version.V2_4
        val values = tag.tags
        val frames = bytes {
            values.title?.let { text(tag, "TIT2", it) }
            values.artist?.let { text(tag, "TPE1", it) }
            values.album?.let { text(tag, "TALB", it) }
            values.albumArtist?.let { text(tag, "TPE2", it) }
            values.track?.let { text(tag, "TRCK", it) }
            values.disc?.let { text(tag, "TPOS", it) }
            values.year?.let { text(tag, if (v24) "TDRC" else "TYER", it) }
            values.genre?.let { text(tag, "TCON", it) }
            if (values.compilation) text(tag, "TCMP", "1")
            values.cover?.let { frame(tag, "APIC", picture(it)) }
        }
        return bytes {
            ascii("ID3")
            u8(tag.version.major)
            u8(0) // revision
            u8(0) // no flags
            u32(synchsafe(frames.size + PADDING))
            append(frames)
            zeros(PADDING)
        }
    }

    private fun Bytes.text(tag: Mp3Tags.Id3v2, id: String, value: String) {
        val payload = bytes {
            u8(encodingByte(tag.encoding))
            // Several values, which only ID3v2.4 has, are separated by the encoding's terminator.
            value.split('\u0000').forEachIndexed { index, part ->
                if (index > 0) zeros(if (tag.encoding == TextEncoding.UTF_16) 2 else 1)
                append(encode(tag.encoding, part))
            }
        }
        frame(tag, id, payload)
    }

    private fun Bytes.frame(tag: Mp3Tags.Id3v2, id: String, payload: ByteArray) {
        ascii(id)
        u32(if (tag.version == Id3Version.V2_4) synchsafe(payload.size) else payload.size)
        u16(0) // no flags
        append(payload)
    }

    private fun picture(cover: Cover) = bytes {
        u8(encodingByte(TextEncoding.LATIN_1))
        ascii("image/png")
        u8(0)
        u8(PICTURE_FRONT_COVER)
        u8(0) // an empty description
        append(cover.png())
    }

    private fun encodingByte(encoding: TextEncoding) = when (encoding) {
        TextEncoding.LATIN_1, TextEncoding.CP1252_AS_LATIN_1 -> 0
        TextEncoding.UTF_16 -> 1
        TextEncoding.UTF_8 -> 3
    }

    private fun encode(encoding: TextEncoding, value: String): ByteArray = when (encoding) {
        TextEncoding.LATIN_1 -> strictly(value, Charsets.ISO_8859_1)
        TextEncoding.CP1252_AS_LATIN_1 -> strictly(value, CP1252)
        TextEncoding.UTF_16 -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE)
        TextEncoding.UTF_8 -> value.toByteArray(Charsets.UTF_8)
    }

    // A fixture's text is written exactly, so a character the charset doesn't have is a mistake in the fixture.
    private fun strictly(value: String, charset: Charset): ByteArray {
        require(charset.newEncoder().canEncode(value)) { "'$value' can't be written in $charset" }
        return value.toByteArray(charset)
    }

    private fun synchsafe(value: Int): Int =
        (value and 0x7F) or ((value shl 1) and 0x7F00) or ((value shl 2) and 0x7F0000) or
            ((value shl 3) and 0x7F000000)
}
