package com.eduardoflores.rolabox.device.library.testing.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M4aFixturesTest {
    private val m4as = AudioFixtures.sets.flatMap { it.fixtures }.filter { it.content is FixtureContent.M4a }

    @Test
    fun `every m4a is an M4A with its index before or after its audio`() {
        m4as.forEach { fixture ->
            val stream = (fixture.content as FixtureContent.M4a).stream
            val types = fixture.bytes().atoms().map { it.type }

            assertEquals(fixture.path, "ftyp", types.first())
            val expected = if (stream.moovFirst) listOf("ftyp", "moov", "mdat") else listOf("ftyp", "mdat", "moov")
            assertEquals(fixture.path, expected, types)
        }
    }

    @Test
    fun `the sample table points at the frames in mdat`() {
        m4as.forEach { fixture ->
            val stream = (fixture.content as FixtureContent.M4a).stream
            val bytes = fixture.bytes()
            val mdat = bytes.atom("mdat")!!
            val stco = bytes.atom("moov/trak/mdia/minf/stbl/stco")!!
            val stsz = bytes.atom("moov/trak/mdia/minf/stbl/stsz")!!

            assertEquals(fixture.path, mdat.payloadStart, bytes.u32(stco.payloadStart + 8))
            val frameSize = bytes.u32(stsz.payloadStart + 4)
            assertEquals(fixture.path, stream.frameCount, bytes.u32(stsz.payloadStart + 8))
            assertEquals(fixture.path, stream.frameCount * frameSize, mdat.end - mdat.payloadStart)
        }
    }

    @Test
    fun `the track's timescale is its sample rate, and its duration is its frames`() {
        m4as.forEach { fixture ->
            val stream = (fixture.content as FixtureContent.M4a).stream
            val bytes = fixture.bytes()
            val mdhd = bytes.atom("moov/trak/mdia/mdhd")!!

            assertEquals(fixture.path, stream.sampleRate, bytes.u32(mdhd.payloadStart + 12))
            assertEquals(
                fixture.path,
                stream.frameCount * AacStream.SAMPLES_PER_FRAME,
                bytes.u32(mdhd.payloadStart + 16),
            )
        }
    }

    @Test
    fun `the tags read back from ilst`() {
        val fixture = AudioFixtures.clean.fixtures.first { it.path.startsWith("Mireille Okafor/Two Rooms/2-01") }
        val tags = (fixture.content as FixtureContent.M4a).tags!!
        val bytes = fixture.bytes()

        assertEquals(tags.title, bytes.ilstText("©nam"))
        assertEquals(tags.artist, bytes.ilstText("©ART"))
        assertEquals(tags.album, bytes.ilstText("©alb"))
        assertEquals(tags.albumArtist, bytes.ilstText("aART"))
        assertEquals(tags.year, bytes.ilstText("©day"))
        assertEquals(tags.genre, bytes.ilstText("©gen"))

        val (_, track) = bytes.ilstData("trkn")!!
        assertEquals(1 to 3, track.u16(2) to track.u16(4))
        val (_, disc) = bytes.ilstData("disk")!!
        assertEquals(2 to 2, disc.u16(2) to disc.u16(4))
        val (coverType, cover) = bytes.ilstData("covr")!!
        assertEquals(14, coverType)
        assertTrue(cover.startsWith(PNG_SIGNATURE))
    }

    @Test
    fun `a numbered genre is gnre, a compilation is cpil, and a track that isn't a number is 0`() {
        val genre = AudioFixtures.broken.fixtures.single { it.case.startsWith("m4a: genre 13") }.bytes()
        val (_, gnre) = genre.ilstData("gnre")!!
        assertEquals(14, gnre.u16(0))
        assertNull(genre.ilstData("©gen"))

        val compilation = AudioFixtures.broken.fixtures.first {
            it.fileName.endsWith(".m4a") &&
                it.case.startsWith("a compilation")
        }
        assertEquals(21 to 1, compilation.bytes().ilstData("cpil")!!.let { (type, value) -> type to value.u8(0) })

        val track = AudioFixtures.broken.fixtures.single {
            it.case.startsWith("m4a: a track number that isn't")
        }.bytes()
        assertEquals(0, track.ilstData("trkn")!!.second.u16(2))
    }

    @Test
    fun `an untagged m4a has no udta`() {
        val bytes = AudioFixtures.broken.fixtures.single { it.case == "no tags: m4a" }.bytes()

        assertNull(bytes.atom("moov/udta"))
    }

    @Test
    fun `the AudioSpecificConfig is AAC-LC at the stream's rate and channels`() {
        val config = AacStream(sampleRate = 48_000, channels = 1).audioSpecificConfig()

        // 00010 0011 0001 000: AAC-LC, index 3 (48 kHz), one channel.
        assertEquals(0b0001_0001_1000_1000, config.u16(0))
    }

    private fun ByteArray.ilstText(item: String): String? = ilstData(item)?.let { (type, value) ->
        assertEquals(1, type)
        value.decodeToString()
    }
}
