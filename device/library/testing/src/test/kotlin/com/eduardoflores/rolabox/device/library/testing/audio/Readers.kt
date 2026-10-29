package com.eduardoflores.rolabox.device.library.testing.audio

import java.nio.charset.Charset

// Minimal readers that read the fixtures back, independently of the writers, so the tests check the
// bytes against the formats' layouts. They're not the library's parser (library/05).

internal fun ByteArray.u8(at: Int) = this[at].toInt() and 0xFF

internal fun ByteArray.u16(at: Int) = (u8(at) shl 8) or u8(at + 1)

internal fun ByteArray.u32(at: Int) = (u16(at) shl 16) or u16(at + 2)

internal fun ByteArray.ascii(at: Int, length: Int) = String(this, at, length, Charsets.ISO_8859_1)

internal fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && copyOf(prefix.size).contentEquals(prefix)

internal val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

/** An ID3v2 tag: its version, its frames by id, and its total size with the header. */
internal class Id3v2Tag(val major: Int, val frames: Map<String, ByteArray>, val size: Int) {
    /** A text frame's text, decoded as its encoding byte declares, with several values joined by `\u0000`. */
    fun text(id: String, declaredLatin1: Charset = Charsets.ISO_8859_1): String? {
        val frame = frames[id] ?: return null
        val body = frame.copyOfRange(1, frame.size)
        return when (frame[0].toInt()) {
            0 -> String(body, declaredLatin1)
            1 -> String(body, Charsets.UTF_16).replace("", "")
            3 -> String(body, Charsets.UTF_8)
            else -> error("Unexpected encoding ${frame[0]}")
        }
    }

    companion object {
        fun read(bytes: ByteArray): Id3v2Tag? {
            if (bytes.ascii(0, 3) != "ID3") return null
            val major = bytes.u8(3)
            val size = 10 + synchsafe(bytes.u32(6))
            val frames = mutableMapOf<String, ByteArray>()
            var at = 10
            while (at + 10 <= size && bytes[at].toInt() != 0) {
                val id = bytes.ascii(at, 4)
                val length = if (major == 4) synchsafe(bytes.u32(at + 4)) else bytes.u32(at + 4)
                frames[id] = bytes.copyOfRange(at + 10, at + 10 + length)
                at += 10 + length
            }
            return Id3v2Tag(major, frames, size)
        }

        private fun synchsafe(value: Int) =
            (value and 0x7F) or ((value shr 8 and 0x7F) shl 7) or ((value shr 16 and 0x7F) shl 14) or
                ((value shr 24 and 0x7F) shl 21)
    }
}

/** An MPEG audio frame header. */
internal data class MpegFrame(
    val mpeg1: Boolean,
    val bitrateKbps: Int,
    val sampleRate: Int,
    val mono: Boolean,
    val length: Int,
)

internal fun ByteArray.mpegFrameAt(at: Int): MpegFrame? {
    val synced = at + 4 <= size && u8(at) == 0xFF && u8(at + 1) and 0xE0 == 0xE0
    val layer3 = synced && (u8(at + 1) shr 1 and 0x3) == 0b01
    if (!layer3) return null
    val mpeg1 = (u8(at + 1) shr 3 and 0x3) == 0b11
    val version = if (mpeg1) MpegVersion.MPEG_1 else MpegVersion.MPEG_2
    val bitrate = version.bitratesKbps[(u8(at + 2) shr 4) - 1]
    val sampleRate = version.sampleRates[u8(at + 2) shr 2 and 0x3]
    val padding = u8(at + 2) shr 1 and 0x1
    val mono = (u8(at + 3) shr 6) == 0b11
    val length = (if (mpeg1) 144 else 72) * 1000 * bitrate / sampleRate + padding
    return MpegFrame(mpeg1, bitrate, sampleRate, mono, length)
}

/** The MPEG frames of an MP3, between its ID3v2 tag and its ID3v1 tag. */
internal fun ByteArray.mpegFrames(): List<Pair<Int, MpegFrame>> {
    val start = Id3v2Tag.read(this)?.size ?: 0
    val end = if (size >= 128 && ascii(size - 128, 3) == "TAG") size - 128 else size
    val frames = mutableListOf<Pair<Int, MpegFrame>>()
    var at = start
    while (at < end) {
        val frame = mpegFrameAt(at) ?: error("No MPEG frame at $at")
        frames += at to frame
        at += frame.length
    }
    check(at == end) { "The last frame runs past the audio" }
    return frames
}

/** An MP4 box: its type, and where its payload is in the file. */
internal class Atom(val type: String, val start: Int, val payloadStart: Int, val end: Int)

internal fun ByteArray.atoms(from: Int = 0, to: Int = size): List<Atom> {
    val atoms = mutableListOf<Atom>()
    var at = from
    while (at + 8 <= to) {
        val length = u32(at)
        check(length >= 8 && at + length <= to) { "A box at $at runs past its parent" }
        atoms += Atom(ascii(at + 4, 4), at, at + 8, at + length)
        at += length
    }
    return atoms
}

// Boxes whose payload starts with a version and flags, before their children or fields.
private val FULL_BOXES = setOf("meta")

/** The box at a path such as `moov/udta/meta/ilst`. */
internal fun ByteArray.atom(path: String): Atom? {
    var found: Atom? = null
    var childrenStart = 0
    var childrenEnd = size
    for (type in path.split('/')) {
        found = atoms(childrenStart, childrenEnd).firstOrNull { it.type == type } ?: return null
        childrenStart = found.payloadStart + if (type in FULL_BOXES) 4 else 0
        childrenEnd = found.end
    }
    return found
}

/** The payload of an `ilst` item's `data` box, after its type and locale. */
internal fun ByteArray.ilstData(item: String): Pair<Int, ByteArray>? {
    val ilst = atom("moov/udta/meta/ilst") ?: return null
    return atoms(ilst.payloadStart, ilst.end).firstOrNull { it.type == item }?.let { box ->
        val data = atoms(box.payloadStart, box.end).single { it.type == "data" }
        u32(data.payloadStart) to copyOfRange(data.payloadStart + 8, data.end)
    }
}
