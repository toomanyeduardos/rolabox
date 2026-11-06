@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.local.impl.tags

import com.eduardoflores.rolabox.device.library.local.api.FileTags

private const val HEADER = 10
private const val MAX_TEXT_FRAME = 64 * 1024

// A tag unsynchronised as a whole has to be read whole. Past this, it is left to the other sources.
private const val MAX_UNSYNCHRONISED_TAG = 16 * 1024 * 1024
private const val UNSYNCHRONISED = 0x80
private const val EXTENDED_HEADER = 0x40
private const val V22_COMPRESSED = 0x40
private const val V23_COMPRESSED = 0x80
private const val V23_ENCRYPTED = 0x40
private const val V23_GROUPED = 0x20
private const val V24_GROUPED = 0x40
private const val V24_COMPRESSED = 0x08
private const val V24_ENCRYPTED = 0x04
private const val V24_UNSYNCHRONISED = 0x02
private const val V24_DATA_LENGTH = 0x01

private val V22_IDS = mapOf(
    "TT2" to "TIT2",
    "TP1" to "TPE1",
    "TP2" to "TPE2",
    "TAL" to "TALB",
    "TRK" to "TRCK",
    "TPA" to "TPOS",
    "TYE" to "TYER",
    "TCP" to "TCMP",
    "TLE" to "TLEN",
    "TCO" to "TCON",
    "PIC" to "APIC",
)
private val TEXT_IDS = setOf("TIT2", "TPE1", "TPE2", "TALB", "TRCK", "TPOS", "TYER", "TDRC", "TCMP", "TLEN", "TCON")

// The one frame whose several values are all read: a song can have several genres.
private const val GENRE = "TCON"

/** ID3v2.2 to v2.4, at the start of the file. Null when there's no tag this can read. */
internal fun readId3v2(source: ByteSource): FileTags? {
    val header = source.read(0, HEADER)
    val readable = header.isId3v2Header() && !(header.u8(3) == 2 && header.u8(5) and V22_COMPRESSED != 0)
    val frames = if (readable) framesOf(source, header) else null
    return frames?.let { FrameReader(it, header.u8(3)).read(firstFrameAt(it, header)) }
}

// v2.4 marks each frame instead, and its tag's flag only says some frame is.
private fun framesOf(source: ByteSource, header: ByteArray): ByteSource? {
    val size = header.synchsafe(6)
    val unsynchronised = header.u8(3) < 4 && header.u8(5) and UNSYNCHRONISED != 0
    return when {
        !unsynchronised -> Window(source, HEADER.toLong(), minOf(HEADER + size, source.size))
        size > MAX_UNSYNCHRONISED_TAG -> null
        else -> ArrayByteSource(source.read(HEADER.toLong(), size.toInt()).removeUnsynchronisation())
    }
}

private fun firstFrameAt(frames: ByteSource, header: ByteArray): Long =
    if (header.u8(5) and EXTENDED_HEADER != 0) frames.extendedHeaderSize(header.u8(3)) else 0

private fun ByteArray.isId3v2Header() = size == HEADER && ascii(0, 3) == "ID3" && u8(3) in 2..4 &&
    u8(4) != 0xFF && (6 until HEADER).all { u8(it) < 0x80 }

// v2.3's size leaves itself out, and v2.4's counts itself.
private fun ByteSource.extendedHeaderSize(major: Int): Long {
    val bytes = read(0, 4)
    return when {
        bytes.size < 4 -> size
        major == 3 -> 4 + bytes.bigEndian(0, 4)
        else -> bytes.synchsafe(0)
    }
}

private fun ByteArray.removeUnsynchronisation(): ByteArray {
    val result = ByteArray(size)
    var length = 0
    for (index in indices) {
        val afterFf = index > 0 && u8(index - 1) == 0xFF
        if (!(afterFf && this[index] == 0.toByte())) result[length++] = this[index]
    }
    return result.copyOf(length)
}

/** The bytes of [source] from [start] until [end], as a source of their own. */
private class Window(private val source: ByteSource, private val start: Long, end: Long) : ByteSource {
    override val size: Long = (end - start).coerceAtLeast(0)

    override fun read(position: Long, length: Int): ByteArray {
        val available = (size - position).coerceAtLeast(0)
        return source.read(start + position, minOf(length.toLong(), available).toInt())
    }
}

private class FrameReader(private val frames: ByteSource, private val major: Int) {
    private val headerSize = if (major == 2) 6 else 10
    private val idSize = if (major == 2) 3 else 4
    private val texts = HashMap<String, String>()
    private var hasPicture = false

    fun read(start: Long): FileTags {
        var position = start
        while (position + headerSize <= frames.size) {
            val header = frames.read(position, headerSize)
            if (header.size < headerSize || !header.hasFrameId()) break
            val payloadAt = position + headerSize
            val payloadSize = frameSize(header, payloadAt)
            readFrame(header, payloadAt, payloadSize)
            position = payloadAt + payloadSize
        }
        return toFileTags()
    }

    // After the last frame comes the padding, or garbage in a broken tag. Either ends the frames.
    private fun ByteArray.hasFrameId() = (0 until idSize).all { index ->
        val char = u8(index).toChar()
        char in 'A'..'Z' || char in '0'..'9'
    }

    private fun frameSize(header: ByteArray, payloadAt: Long): Long = when (major) {
        2 -> header.bigEndian(3, 3)
        3 -> header.bigEndian(4, 4)
        else -> v24FrameSize(header, payloadAt)
    }

    // iTunes wrote v2.4's sizes as plain numbers. Only what comes after the frame tells them apart.
    private fun v24FrameSize(header: ByteArray, payloadAt: Long): Long {
        val plain = header.bigEndian(4, 4)
        val synchsafe = header.synchsafe(4)
        return when {
            plain == synchsafe -> plain
            (4..7).any { header.u8(it) >= 0x80 } -> plain
            endsFrame(payloadAt + synchsafe) || !endsFrame(payloadAt + plain) -> synchsafe
            else -> plain
        }
    }

    private fun endsFrame(position: Long): Boolean {
        if (position >= frames.size) return position == frames.size
        val next = frames.read(position, idSize)
        return next.size == idSize && (next[0] == 0.toByte() || next.hasFrameId())
    }

    private fun readFrame(header: ByteArray, payloadAt: Long, size: Long) {
        val rawId = header.ascii(0, idSize)
        val id = if (major == 2) V22_IDS[rawId] ?: return else rawId
        if (id == "APIC") {
            // Its bytes are skipped: only that it is there is recorded.
            hasPicture = true
        } else if (id in TEXT_IDS && size in 1..MAX_TEXT_FRAME && id !in texts) {
            val flags = if (major == 2) 0 else header.u8(9)
            val text = frames.read(payloadAt, size.toInt()).withoutFrameFlags(flags)?.decodeTextFrame()
            val value = if (id == GENRE) text else text?.cleanTagText()
            value?.let { texts[id] = it }
        }
    }

    /** The frame's text bytes, or null when its flags say they can't be read: compressed or encrypted. */
    private fun ByteArray.withoutFrameFlags(flags: Int): ByteArray? = when (major) {
        2 -> this

        3 -> when {
            flags and (V23_COMPRESSED or V23_ENCRYPTED) != 0 -> null
            flags and V23_GROUPED != 0 -> drop(1)
            else -> this
        }

        else -> when {
            flags and (V24_COMPRESSED or V24_ENCRYPTED) != 0 -> null

            else -> {
                val prefix = (if (flags and V24_GROUPED != 0) 1 else 0) + (if (flags and V24_DATA_LENGTH != 0) 4 else 0)
                val bytes = drop(prefix)
                if (flags and V24_UNSYNCHRONISED != 0) bytes.removeUnsynchronisation() else bytes
            }
        }
    }

    private fun ByteArray.drop(count: Int) = copyOfRange(minOf(count, size), size)

    // The whole text, every value of it, since only the genre's are all read.
    private fun ByteArray.decodeTextFrame(): String? {
        if (isEmpty()) return null
        val text = copyOfRange(1, size)
        return when (u8(0)) {
            1 -> text.decodeUtf16()
            2 -> text.decodeUtf16Be()
            3 -> text.toString(Charsets.UTF_8)
            else -> text.decodeLegacy()
        }
    }

    private fun toFileTags() = FileTags(
        title = texts["TIT2"],
        artist = texts["TPE1"],
        albumArtist = texts["TPE2"],
        album = texts["TALB"],
        trackNumber = texts["TRCK"]?.tagNumber(),
        discNumber = texts["TPOS"]?.tagNumber(),
        year = (texts["TDRC"] ?: texts["TYER"])?.tagYear(),
        compilation = texts["TCMP"]?.tagFlag(),
        durationMillis = texts["TLEN"]?.trim()?.toLongOrNull()?.takeIf { it > 0 },
        hasEmbeddedPicture = hasPicture,
        genres = genresOf(texts[GENRE]),
    )
}
