@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

/**
 * Writes an MP4 file with one AAC track, and iTunes-style tags in `moov/udta/meta/ilst`.
 *
 * Values that only fill their place in a box's layout are written inline, with a comment, as the
 * specification lays them out. Values that mean something beyond their place are named below.
 */
internal object M4aFile {
    // An MP4 states every duration in ticks of a timescale (ticks per second). The movie's is 1000, so
    // the durations in mvhd and tkhd are milliseconds. The track's own timescale, in mdhd, is the
    // sample rate, so its duration is its number of samples. Players read the file's length from these.
    private const val MOVIE_TIMESCALE = 1_000

    // The track's ISO 639-2 language, "und" (undetermined), packed as MP4 stores it: three 5-bit letters.
    private const val LANGUAGE_UNDETERMINED = 0x55C4

    // The type code at the start of every ilst item's data box, which says how to read its payload:
    // binary laid out per item (trkn, disk, gnre), UTF-8 text, a PNG cover, or a big-endian integer (cpil).
    private const val DATA_IMPLICIT = 0
    private const val DATA_UTF_8 = 1
    private const val DATA_PNG = 14
    private const val DATA_INTEGER = 21

    fun bytes(stream: AacStream, tags: Tags?): ByteArray {
        val frame = stream.silentFrame()
        val ftyp = atom("ftyp") {
            ascii("M4A ")
            u32(0)
            ascii("M4A ")
            ascii("mp42")
            ascii("isom")
        }
        val mdat = atom("mdat") { repeat(stream.frameCount) { append(frame) } }
        val moovSize = moov(stream, frame.size, tags, chunkOffset = 0).size
        return if (stream.moovFirst) {
            val moov = moov(stream, frame.size, tags, chunkOffset = ftyp.size + moovSize + 8)
            ftyp + moov + mdat
        } else {
            val moov = moov(stream, frame.size, tags, chunkOffset = ftyp.size + 8)
            ftyp + mdat + moov
        }
    }

    private fun moov(stream: AacStream, frameSize: Int, tags: Tags?, chunkOffset: Int): ByteArray {
        val durationMs = (stream.playedDurationUs / 1_000).toInt()
        return atom("moov") {
            fullBox("mvhd") {
                u32(0) // created
                u32(0) // modified
                u32(MOVIE_TIMESCALE)
                u32(durationMs)
                u32(0x00010000) // rate 1.0
                u16(0x0100) // volume 1.0
                zeros(10)
                matrix()
                zeros(24)
                u32(2) // next track id
            }
            track(stream, durationMs, frameSize, chunkOffset)
            if (tags != null) {
                box("udta") {
                    fullBox("meta") {
                        handler("mdir", vendor = "appl", name = "")
                        box("ilst") { items(tags) }
                    }
                }
            }
        }
    }

    private fun Bytes.track(stream: AacStream, durationMs: Int, frameSize: Int, chunkOffset: Int) = box("trak") {
        fullBox("tkhd", flags = 0x7) {
            // enabled, in the movie and in the preview
            u32(0)
            u32(0)
            u32(1) // track id
            u32(0)
            u32(durationMs)
            zeros(8)
            u16(0) // layer
            u16(0) // alternate group
            u16(0x0100) // volume 1.0
            u16(0)
            matrix()
            u32(0) // width
            u32(0) // height
        }
        box("mdia") {
            fullBox("mdhd") {
                u32(0)
                u32(0)
                u32(stream.sampleRate)
                u32(stream.frameCount * AacStream.SAMPLES_PER_FRAME)
                u16(LANGUAGE_UNDETERMINED)
                u16(0)
            }
            handler("soun", vendor = "", name = "SoundHandler")
            box("minf") {
                fullBox("smhd") { u32(0) }
                box("dinf") {
                    fullBox("dref") {
                        u32(1)
                        fullBox("url ", flags = 1) {} // the media is in this file
                    }
                }
                sampleTable(stream, frameSize, chunkOffset)
            }
        }
    }

    private fun Bytes.sampleTable(stream: AacStream, frameSize: Int, chunkOffset: Int) = box("stbl") {
        fullBox("stsd") {
            u32(1)
            box("mp4a") {
                zeros(6)
                u16(1) // data reference index
                zeros(8)
                u16(stream.channels)
                u16(16) // sample size
                u32(0)
                u32(stream.sampleRate shl 16)
                fullBox("esds") { elementaryStreamDescriptor(stream) }
            }
        }
        fullBox("stts") {
            u32(1)
            u32(stream.frameCount)
            u32(AacStream.SAMPLES_PER_FRAME)
        }
        fullBox("stsc") {
            // every frame in one chunk
            u32(1)
            u32(1)
            u32(stream.frameCount)
            u32(1)
        }
        fullBox("stsz") {
            u32(frameSize) // every frame has the same size
            u32(stream.frameCount)
        }
        fullBox("stco") {
            u32(1)
            u32(chunkOffset)
        }
    }

    private fun Bytes.elementaryStreamDescriptor(stream: AacStream) {
        val decoderSpecificInfo = descriptor(0x05, stream.audioSpecificConfig())
        val decoderConfig = descriptor(
            0x04,
            bytes {
                u8(0x40) // MPEG-4 audio
                u8(0x15) // an audio stream
                u24(0) // buffer size
                u32(0) // max bitrate
                u32(0) // average bitrate
                append(decoderSpecificInfo)
            },
        )
        val slConfig = descriptor(0x06, byteArrayOf(0x02))
        append(
            descriptor(
                0x03,
                bytes {
                    u16(0) // elementary stream id
                    u8(0) // no flags
                    append(decoderConfig)
                    append(slConfig)
                },
            ),
        )
    }

    private fun descriptor(tag: Int, payload: ByteArray) = bytes {
        u8(tag)
        u8(payload.size)
        append(payload)
    }

    private fun Bytes.items(tags: Tags) {
        tags.title?.let { text("©nam", it) }
        tags.artist?.let { text("©ART", it) }
        tags.album?.let { text("©alb", it) }
        tags.albumArtist?.let { text("aART", it) }
        tags.year?.let { text("©day", it) }
        tags.genre?.let { genre ->
            // A number is written as iTunes writes a standard genre: gnre, the ID3v1 number plus one.
            val number = genre.removeSurrounding("(", ")").toIntOrNull()
            if (number != null) item("gnre", DATA_IMPLICIT, bytes { u16(number + 1) }) else text("©gen", genre)
        }
        tags.track?.let { item("trkn", DATA_IMPLICIT, numberPair(it, trailing = 2)) }
        tags.disc?.let { item("disk", DATA_IMPLICIT, numberPair(it, trailing = 0)) }
        if (tags.compilation) item("cpil", DATA_INTEGER, byteArrayOf(1))
        tags.cover?.let { item("covr", DATA_PNG, it.png()) }
    }

    private fun Bytes.text(name: String, value: String) = item(name, DATA_UTF_8, value.toByteArray(Charsets.UTF_8))

    private fun Bytes.item(name: String, type: Int, payload: ByteArray) = box(name) {
        box("data") {
            u32(type)
            u32(0) // locale
            append(payload)
        }
    }

    // trkn and disk hold the number and the total as integers, so text that isn't a number is written as 0.
    private fun numberPair(value: String, trailing: Int) = bytes {
        u16(0)
        u16(value.substringBefore('/').toIntOrNull() ?: 0)
        u16(value.substringAfter('/', "").toIntOrNull() ?: 0)
        zeros(trailing)
    }
}
