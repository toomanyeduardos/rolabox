@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

// The MP4 box layouts that M4aFile is built from (ISO/IEC 14496-12).
//
// An MP4 file is a tree of boxes (also called atoms). Every box starts with its total size in bytes
// and a four-character type, such as moov or mdat, and its payload is either fields or more boxes.
// A reader skips a box it doesn't know by its size, which is how a parser finds the tags without
// reading the audio. Values that only fill their place in a box are written inline, with a comment.

/** A box: its size (counting the 8 bytes of size and type), its four-character type, and its payload. */
internal fun atom(type: String, content: Bytes.() -> Unit): ByteArray {
    val payload = bytes(content)
    return bytes {
        u32(8 + payload.size)
        ascii(type)
        append(payload)
    }
}

/** A box written into its parent's payload, where [atom] returns it on its own, as a top-level box. */
internal fun Bytes.box(type: String, content: Bytes.() -> Unit) = append(atom(type, content))

/**
 * A box whose payload starts with a version and flags. The version says which layout of the box's
 * fields follows (0 is the 32-bit one, which every box here uses), and the flags are per box type.
 */
internal fun Bytes.fullBox(type: String, flags: Int = 0, content: Bytes.() -> Unit) = box(type) {
    u8(0) // version
    u24(flags)
    content()
}

/**
 * A handler box, which says what the box around it holds: `soun` for an audio track, `mdir` for
 * iTunes-style tags. [vendor] is `appl` for the tags, as iTunes writes it, and [name] is free text.
 */
internal fun Bytes.handler(type: String, vendor: String, name: String) = fullBox("hdlr") {
    u32(0) // pre-defined
    ascii(type)
    ascii(vendor.padEnd(4, '\u0000'))
    zeros(8) // reserved
    ascii(name)
    u8(0) // the name's terminator
}

/**
 * The identity transformation matrix of a movie and a track header: no scaling, rotation or move.
 * It's 3×3 in fixed point, 16.16 for most values and 2.30 for the last column, so 1 is `0x00010000`
 * in the first two columns and `0x40000000` in the third.
 */
internal fun Bytes.matrix() {
    u32(0x00010000)
    u32(0)
    u32(0)
    u32(0)
    u32(0x00010000)
    u32(0)
    u32(0)
    u32(0)
    u32(0x40000000)
}
