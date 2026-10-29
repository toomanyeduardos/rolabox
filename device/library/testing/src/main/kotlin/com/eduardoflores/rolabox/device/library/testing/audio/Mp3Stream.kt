@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

/**
 * The audio frames of an MP3: MPEG Layer III frames of silence. Their side information is all zeros,
 * so they carry no samples, but every frame is valid and a decoder plays them as silence of the
 * stream's real duration. No encoder is needed, so nothing is committed.
 *
 * One bitrate is a constant bitrate. Several are a variable bitrate: the frames cycle through them,
 * and a first Xing frame gives the frame count, as LAME writes it.
 *
 * An MP3 is nothing but frames back to back, each one decodable on its own. A frame is a 4-byte
 * header (what [header] writes), then the side information (where the frame's audio data is and how
 * to decode it), then that audio data. Each frame holds a fixed number of samples, so the file's
 * duration is its number of frames, and the bitrate only decides how many bytes each frame takes.
 *
 * Values that only fill their place in the header are written inline, with a comment, as the
 * specification lays them out. Values that differ between MPEG versions are in [MpegVersion].
 */
data class Mp3Stream(
    val version: MpegVersion = MpegVersion.MPEG_1,
    val sampleRate: Int = 44_100,
    val channels: Int = 2,
    val bitratesKbps: List<Int> = listOf(128),
    val durationMs: Int = 2_000,
) {
    init {
        require(sampleRate in version.sampleRates) { "$version has no sample rate $sampleRate" }
        require(channels == 1 || channels == 2) { "MP3 is mono or stereo" }
        require(bitratesKbps.isNotEmpty() && bitratesKbps.all { it in version.bitratesKbps }) {
            "$version Layer III has no bitrate in $bitratesKbps"
        }
    }

    val isVbr: Boolean get() = bitratesKbps.size > 1

    /** The frames that carry audio, not counting a Xing frame. */
    val frameCount: Int get() = ceilDiv(durationMs.toLong() * sampleRate, 1_000L * version.samplesPerFrame).toInt()

    /** The duration a decoder plays: whole frames, so a little longer than [durationMs]. */
    val playedDurationUs: Long get() = frameCount.toLong() * version.samplesPerFrame * 1_000_000 / sampleRate

    fun bytes(): ByteArray {
        val audio = Bytes()
        // A frame's exact length is rarely a whole number of bytes (417.96 at 128 kbps and 44.1 kHz),
        // so frames are rounded down, and a frame gets one padding byte whenever the dropped fractions
        // add up to a byte. That keeps the average bitrate exact, as an encoder does.
        var paddingCarry = 0
        repeat(frameCount) { index ->
            val bitrate = bitratesKbps[index % bitratesKbps.size]
            val remainder = (version.frameLengthFactor * 1_000 * bitrate) % sampleRate
            paddingCarry += remainder
            val padded = paddingCarry >= sampleRate
            if (padded) paddingCarry -= sampleRate
            audio.append(silentFrame(bitrate, padded))
        }
        if (!isVbr) return audio.toByteArray()

        val xing = xingFrame(bitratesKbps.max(), audioBytes = audio.size)
        return xing + audio.toByteArray()
    }

    // The frame's bytes are its samples' share of the bitrate: samples per frame / 8 bits per byte ×
    // bits per second / samples per second. frameLengthFactor is the first part, samples per frame / 8.
    private fun frameLength(bitrateKbps: Int, padded: Boolean) =
        version.frameLengthFactor * 1_000 * bitrateKbps / sampleRate + if (padded) 1 else 0

    private fun header(bitrateKbps: Int, padded: Boolean): ByteArray = BitWriter()
        .write(0x7FF, 11) // frame sync
        .write(version.bits, 2)
        .write(0b01, 2) // Layer III
        .write(1, 1) // no CRC
        // The header holds the bitrate and the sample rate as positions in the version's tables.
        // Bitrate position 0 means "free format", so the table starts at 1.
        .write(version.bitratesKbps.indexOf(bitrateKbps) + 1, 4)
        .write(version.sampleRates.indexOf(sampleRate), 2)
        .write(if (padded) 1 else 0, 1)
        .write(0, 1) // private
        .write(if (channels == 1) 0b11 else 0b00, 2) // mono, or stereo
        .write(0, 2) // mode extension
        .write(0, 1) // not copyrighted
        .write(1, 1) // original
        .write(0, 2) // no emphasis
        .toByteArray()

    private fun silentFrame(bitrateKbps: Int, padded: Boolean): ByteArray {
        val frame = ByteArray(frameLength(bitrateKbps, padded))
        header(bitrateKbps, padded).copyInto(frame)
        return frame
    }

    // A silent frame whose main data carries the Xing tag, right after the side information. A
    // variable bitrate stream's duration can't be worked out from its first frame's bitrate, so
    // players read the frame count from here. Decoders recognise the frame and don't play it.
    private fun xingFrame(bitrateKbps: Int, audioBytes: Int): ByteArray {
        val frame = silentFrame(bitrateKbps, padded = false)
        val tag = bytes {
            ascii("Xing")
            u32(0x3) // the frame count and the byte count follow
            u32(frameCount)
            u32(frame.size + audioBytes)
        }
        tag.copyInto(frame, destinationOffset = 4 + version.sideInfoLength(channels))
        return frame
    }
}

/**
 * What differs between MPEG versions: MPEG-2 is MPEG-1 at half the sample rates, for lower
 * bitrates, with frames of half as many samples.
 *
 * - [bits]: the version's code in a frame header.
 * - [samplesPerFrame]: the samples every Layer III frame holds.
 * - [frameLengthFactor]: [samplesPerFrame] / 8, the frame length's factor (see Mp3Stream.frameLength).
 * - [sampleRates] and [bitratesKbps]: the values a header can name, in the order of its codes.
 */
enum class MpegVersion(
    internal val bits: Int,
    internal val samplesPerFrame: Int,
    internal val frameLengthFactor: Int,
    val sampleRates: List<Int>,
    val bitratesKbps: List<Int>,
) {
    MPEG_1(
        bits = 0b11,
        samplesPerFrame = 1152,
        frameLengthFactor = 144,
        sampleRates = listOf(44_100, 48_000, 32_000),
        bitratesKbps = listOf(32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
    ),
    MPEG_2(
        bits = 0b10,
        samplesPerFrame = 576,
        frameLengthFactor = 72,
        sampleRates = listOf(22_050, 24_000, 16_000),
        bitratesKbps = listOf(8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    ),
    ;

    // The side information's bytes, after the header. It describes each channel of each granule (two
    // in MPEG-1, one in MPEG-2), so it's longer in stereo and in MPEG-1. All zeros means no audio data.
    internal fun sideInfoLength(channels: Int) = when (this) {
        MPEG_1 -> if (channels == 1) 17 else 32
        MPEG_2 -> if (channels == 1) 9 else 17
    }
}

internal fun ceilDiv(a: Long, b: Long) = (a + b - 1) / b
