@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

import java.util.zip.CRC32
import java.util.zip.Deflater

/** A tiny PNG writer, so a cover needs no image library and no file in the repository. */
internal object Png {
    private const val SIDE = 16
    private val SIGNATURE =
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    /** A [SIDE]×[SIDE] square of one 24-bit [rgb] color. */
    fun solid(rgb: Int): ByteArray {
        val header = bytes {
            u32(SIDE)
            u32(SIDE)
            u8(8) // bit depth
            u8(2) // truecolor
            u8(0) // deflate
            u8(0) // adaptive filtering
            u8(0) // no interlace
        }
        val rows = bytes {
            repeat(SIDE) {
                u8(0) // filter: none
                repeat(SIDE) { u24(rgb) }
            }
        }
        return bytes {
            append(SIGNATURE)
            chunk("IHDR", header)
            chunk("IDAT", deflate(rows))
            chunk("IEND", ByteArray(0))
        }
    }

    private fun Bytes.chunk(type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        u32(data.size)
        append(typeBytes)
        append(data)
        u32(crc.value.toInt())
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater().apply {
            setInput(data)
            finish()
        }
        val buffer = ByteArray(data.size + 64)
        val length = deflater.deflate(buffer)
        deflater.end()
        return buffer.copyOf(length)
    }
}
