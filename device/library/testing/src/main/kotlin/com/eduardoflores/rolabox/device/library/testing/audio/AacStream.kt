@file:Suppress("MagicNumber") // Byte layouts: the numbers are the format.

package com.eduardoflores.rolabox.device.library.testing.audio

/**
 * The audio of an M4A: AAC-LC frames of silence. Each raw frame holds only its channels' headers
 * with no spectral data, so a decoder plays it as 1024 samples of silence. No encoder is needed.
 *
 * [moovFirst] puts the index (`moov`) before the audio (`mdat`), as a "fast start" file has it, or
 * after it, as most encoders first write it.
 */
data class AacStream(
    val sampleRate: Int = 44_100,
    val channels: Int = 2,
    val durationMs: Int = 2_000,
    val moovFirst: Boolean = true,
) {
    init {
        require(sampleRate in SAMPLE_RATES) { "AAC has no sample rate $sampleRate" }
        require(channels == 1 || channels == 2) { "The fixtures' AAC is mono or stereo" }
    }

    val frameCount: Int get() = ceilDiv(durationMs.toLong() * sampleRate, 1_000L * SAMPLES_PER_FRAME).toInt()

    val playedDurationUs: Long get() = frameCount.toLong() * SAMPLES_PER_FRAME * 1_000_000 / sampleRate

    internal fun silentFrame(): ByteArray = BitWriter().apply {
        if (channels == 1) {
            write(0, 3) // a single channel element
            write(0, 4) // its instance tag
            silentChannel()
        } else {
            write(1, 3) // a channel pair element
            write(0, 4) // its instance tag
            write(0, 1) // no common window: each channel has its own
            silentChannel()
            silentChannel()
        }
        write(7, 3) // the end element
    }.toByteArray()

    // An individual channel stream with one long window and no scale factor bands, so no spectral data.
    private fun BitWriter.silentChannel() {
        write(100, 8) // global gain
        write(0, 1) // reserved
        write(0, 2) // only a long window
        write(0, 1) // sine window shape
        write(0, 6) // max scale factor band: none
        write(0, 1) // no prediction
        write(0, 1) // no pulse data
        write(0, 1) // no temporal noise shaping
        write(0, 1) // no gain control
    }

    internal fun audioSpecificConfig(): ByteArray = BitWriter()
        .write(2, 5) // AAC-LC
        .write(SAMPLE_RATES.indexOf(sampleRate), 4)
        .write(channels, 4)
        .write(0, 3) // 1024-sample frames, no core coder, no extension
        .toByteArray()

    companion object {
        const val SAMPLES_PER_FRAME = 1024

        // In the order of AAC's sampling frequency index.
        val SAMPLE_RATES =
            listOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000)
    }
}
