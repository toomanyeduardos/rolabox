@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

import java.io.ByteArrayOutputStream

/** A growing byte buffer with the big-endian writes that ID3, MP4 and PNG use. */
internal class Bytes {
    private val out = ByteArrayOutputStream()

    val size: Int get() = out.size()

    fun u8(value: Int) = apply { out.write(value and 0xFF) }

    fun u16(value: Int) = apply {
        u8(value ushr 8)
        u8(value)
    }

    fun u24(value: Int) = apply {
        u8(value ushr 16)
        u16(value)
    }

    fun u32(value: Int) = apply {
        u16(value ushr 16)
        u16(value)
    }

    fun append(value: ByteArray) = apply { out.write(value) }

    fun ascii(value: String) = append(value.toByteArray(Charsets.ISO_8859_1))

    fun zeros(count: Int) = append(ByteArray(count))

    fun toByteArray(): ByteArray = out.toByteArray()
}

internal inline fun bytes(block: Bytes.() -> Unit): ByteArray = Bytes().apply(block).toByteArray()

/** Writes values of any bit width, most significant bit first, as AAC and MPEG headers are laid out. */
internal class BitWriter {
    private val out = ByteArrayOutputStream()
    private var current = 0
    private var bitCount = 0

    fun write(value: Int, bits: Int) = apply {
        for (shift in bits - 1 downTo 0) {
            current = (current shl 1) or ((value ushr shift) and 1)
            bitCount++
            if (bitCount == 8) {
                out.write(current)
                current = 0
                bitCount = 0
            }
        }
    }

    /** Pads the last byte with zero bits. */
    fun toByteArray(): ByteArray {
        if (bitCount > 0) write(0, 8 - bitCount)
        return out.toByteArray()
    }
}
