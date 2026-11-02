@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.local.impl.tags

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

internal fun ByteArray.bigEndian(at: Int, length: Int): Long {
    var value = 0L
    for (index in at until at + length) value = (value shl 8) or u8(index).toLong()
    return value
}

internal fun ByteArray.synchsafe(at: Int): Long {
    var value = 0L
    for (index in at until at + 4) value = (value shl 7) or (u8(index) and 0x7F).toLong()
    return value
}

internal fun ByteArray.ascii(at: Int, length: Int): String = String(this, at, length, Charsets.ISO_8859_1)
