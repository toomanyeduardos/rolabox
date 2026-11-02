package com.eduardoflores.rolabox.device.library.local.impl.tags

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** The bytes of a file, read by position, so the parser can skip what it doesn't need. */
internal interface ByteSource {
    val size: Long

    /** Up to [length] bytes from [position]. Fewer when the file ends first, and none past its end. */
    fun read(position: Long, length: Int): ByteArray
}

internal class ArrayByteSource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, length: Int): ByteArray {
        if (position >= size) return ByteArray(0)
        val start = position.toInt()
        return bytes.copyOfRange(start, minOf(start.toLong() + length, size).toInt())
    }
}

internal class ChannelByteSource(private val channel: FileChannel) : ByteSource {
    override val size: Long = channel.size()

    override fun read(position: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), (size - position).coerceAtLeast(0)).toInt())
        var offset = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, offset)
            if (read <= 0) break
            offset += read
        }
        return buffer.array().copyOf(buffer.position())
    }
}
