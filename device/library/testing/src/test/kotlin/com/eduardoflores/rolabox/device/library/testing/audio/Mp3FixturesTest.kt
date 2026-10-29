package com.eduardoflores.rolabox.device.library.testing.audio

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3FixturesTest {
    private val mp3s = AudioFixtures.sets.flatMap { it.fixtures }.filter { it.content is FixtureContent.Mp3 }

    @Test
    fun `every mp3 is whole MPEG Layer III frames of its stream`() {
        mp3s.forEach { fixture ->
            val stream = (fixture.content as FixtureContent.Mp3).stream
            val frames = fixture.bytes().mpegFrames().map { it.second }
            val xingFrames = if (stream.isVbr) 1 else 0

            assertEquals(fixture.path, stream.frameCount + xingFrames, frames.size)
            frames.forEach { frame ->
                assertEquals(fixture.path, stream.version == MpegVersion.MPEG_1, frame.mpeg1)
                assertEquals(fixture.path, stream.sampleRate, frame.sampleRate)
                assertEquals(fixture.path, stream.channels == 1, frame.mono)
                assertTrue(fixture.path, frame.bitrateKbps in stream.bitratesKbps)
            }
        }
    }

    @Test
    fun `a constant bitrate stream averages its bitrate`() {
        val stream = Mp3Stream(durationMs = 10_000)
        val seconds = stream.playedDurationUs / 1_000_000.0

        assertEquals(128_000.0, stream.bytes().size * 8 / seconds, 100.0)
    }

    @Test
    fun `a variable bitrate stream starts with a Xing frame that counts the frames`() {
        val stream = Mp3Stream(bitratesKbps = listOf(128, 192, 96))
        val bytes = stream.bytes()
        val xing = 4 + 32

        assertEquals(setOf(128, 192, 96), bytes.mpegFrames().map { it.second.bitrateKbps }.toSet())
        assertEquals("Xing", bytes.ascii(xing, 4))
        assertEquals(stream.frameCount, bytes.u32(xing + 8))
        assertEquals(bytes.size, bytes.u32(xing + 12))
    }

    @Test
    fun `ID3v2 text frames read back in every encoding`() {
        val tags = Tags(title = "東京の夜", artist = "Søren", album = "Encodings", track = "3/4", year = "2024")
        listOf(
            Id3Version.V2_3 to TextEncoding.UTF_16,
            Id3Version.V2_4 to TextEncoding.UTF_8,
        ).forEach { (version, encoding) ->
            val tag = Id3v2Tag.read(FixtureContent.Mp3(Mp3Tags.Id3v2(version, encoding, tags)).bytes())!!

            assertEquals(version.major, tag.major)
            assertEquals(tags.title, tag.text("TIT2"))
            assertEquals(tags.artist, tag.text("TPE1"))
            assertEquals(tags.album, tag.text("TALB"))
            assertEquals(tags.track, tag.text("TRCK"))
            assertEquals(tags.year, tag.text(if (version == Id3Version.V2_4) "TDRC" else "TYER"))
        }
    }

    @Test
    fun `Windows-1252 text declares Latin-1, so it reads wrong as Latin-1 and right as Windows-1252`() {
        val fixture = AudioFixtures.broken.fixtures.single { it.case.startsWith("Windows-1252") }
        val tag = Id3v2Tag.read(fixture.bytes())!!

        assertEquals(0, tag.frames.getValue("TIT2")[0].toInt())
        assertEquals("Don’t Look Back…", tag.text("TIT2", Charset.forName("windows-1252")))
        assertNotEquals("Don’t Look Back…", tag.text("TIT2"))
    }

    @Test
    fun `the genre cases are written as they are`() {
        val genres = AudioFixtures.broken.fixtures
            .filter { it.path.startsWith("Genre Lab/") && it.content is FixtureContent.Mp3 }
            .map { Id3v2Tag.read(it.bytes())!!.text("TCON") }

        assertEquals(listOf("(17)", "17", "Rock\u0000Pop\u0000Jazz", "Dream Pop"), genres)
    }

    @Test
    fun `a cover is a front cover PNG in APIC`() {
        val fixture = AudioFixtures.clean.fixtures.first { it.content is FixtureContent.Mp3 }
        val apic = Id3v2Tag.read(fixture.bytes())!!.frames.getValue("APIC")
        val mime = "image/png"

        assertEquals(mime, apic.ascii(1, mime.length))
        assertEquals(3, apic.u8(1 + mime.length + 1))
        assertTrue(apic.copyOfRange(1 + mime.length + 3, apic.size).startsWith(PNG_SIGNATURE))
    }

    @Test
    fun `ID3v1 is the last 128 bytes, with the track of ID3v1_1 and the genre by number`() {
        val bytes = AudioFixtures.broken.fixtures.single { it.case == "ID3v1.1 only" }.bytes()
        val tag = bytes.copyOfRange(bytes.size - 128, bytes.size)

        assertNull(Id3v2Tag.read(bytes))
        assertEquals("TAG", tag.ascii(0, 3))
        assertEquals("Static Bloom", tag.ascii(3, 30).trimEnd('\u0000'))
        assertEquals(0, tag.u8(125))
        assertEquals(5, tag.u8(126))
        assertEquals(17, tag.u8(127))
    }

    @Test
    fun `ID3v1_0 has no track, and its fields are cut at 30 characters`() {
        val noTrack = AudioFixtures.broken.fixtures.single { it.case.startsWith("ID3v1.0 only") }.bytes()
        val long = AudioFixtures.broken.fixtures.single { it.case.contains("cut at its 30") }.bytes()

        assertEquals(0, noTrack.u8(noTrack.size - 2))
        assertEquals("A Title Much Longer Than Thirt", long.ascii(long.size - 125, 30))
    }

    @Test
    fun `a file can have both tags`() {
        val bytes = AudioFixtures.broken.fixtures.single { it.case.startsWith("ID3v2.4 and ID3v1") }.bytes()

        assertEquals("ID3v2.4 and ID3v1", Id3v2Tag.read(bytes)!!.text("TIT2"))
        assertEquals("TAG", bytes.ascii(bytes.size - 128, 3))
    }

    @Test
    fun `an untagged mp3 is only audio frames`() {
        val bytes = AudioFixtures.broken.fixtures.single { it.case == "no tags: mp3" }.bytes()

        assertNull(Id3v2Tag.read(bytes))
        assertFalse(bytes.ascii(bytes.size - 128, 3) == "TAG")
        assertTrue(bytes.mpegFrames().isNotEmpty())
    }
}
