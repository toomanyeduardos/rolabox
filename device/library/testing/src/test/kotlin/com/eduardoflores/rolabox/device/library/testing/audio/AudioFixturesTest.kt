package com.eduardoflores.rolabox.device.library.testing.audio

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioFixturesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `every set writes each fixture at its path, and a test reads it back`() {
        AudioFixtures.sets.forEach { set ->
            val root = folder.newFolder(set.name)
            val files = set.writeTo(root)

            assertEquals(set.fixtures.size, files.size)
            set.fixtures.zip(files).forEach { (fixture, file) ->
                assertEquals(File(root, fixture.path), file)
                assertArrayEquals(fixture.path, fixture.bytes(), file.readBytes())
            }
        }
    }

    @Test
    fun `the fixtures are the same bytes every time`() {
        AudioFixtures.sets.flatMap { it.fixtures }.forEach { fixture ->
            assertArrayEquals(fixture.path, fixture.bytes(), fixture.bytes())
        }
    }

    @Test
    fun `the clean set is laid out as Artist, Album, NN - Title, with full tags`() {
        val layout = Regex("""^(?<artist>[^/]+)/(?<album>[^/]+)/(\d-)?(?<number>\d\d) - (?<title>[^/]+)\.(mp3|m4a)$""")
        AudioFixtures.clean.fixtures.forEach { fixture ->
            val match = layout.matchEntire(fixture.path)
            assertTrue(fixture.path, match != null)
            val part = { name: String -> match!!.groups[name]!!.value }
            val tags = fixture.tags()
            assertEquals(part("title"), tags.title)
            assertEquals(part("artist"), tags.artist)
            assertEquals(part("artist"), tags.albumArtist)
            assertEquals(part("album"), tags.album)
            assertEquals(part("number").toInt(), tags.track!!.substringBefore('/').toInt())
            assertTrue(fixture.path, tags.year != null && tags.genre != null && tags.cover != null)
        }
    }

    @Test
    fun `the clean set has artists with several albums, and an album on two discs`() {
        val albumsByArtist = AudioFixtures.clean.fixtures.map { it.tags() }.groupBy({ it.artist }, { it.album })
        assertTrue(albumsByArtist.values.any { it.distinct().size > 1 })
        assertTrue(albumsByArtist.values.all { albums -> albums.groupingBy { it }.eachCount().values.all { it > 1 } })

        val discs = AudioFixtures.clean.fixtures.mapNotNull { it.tags().disc }.distinct()
        assertEquals(listOf("1/2", "2/2"), discs)
    }

    @Test
    fun `the broken set has the same song in two places, with the clean set's bytes`() {
        val glassHarbor = AudioFixtures.clean.fixtures.single { it.fileName == "02 - Glass Harbor.mp3" }.bytes()
        val copies = AudioFixtures.broken.fixtures.filter { it.fileName.endsWith("Glass Harbor.mp3") }

        assertEquals(2, copies.size)
        copies.forEach { assertArrayEquals(it.path, glassHarbor, it.bytes()) }
    }

    @Test
    fun `the broken set has two albums called Greatest Hits, by different artists`() {
        val greatestHits = AudioFixtures.broken.fixtures.map { it.tags() }.filter { it.album == "Greatest Hits" }

        assertEquals(2, greatestHits.mapNotNull { it.albumArtist }.distinct().size)
    }

    @Test
    fun `the broken set has a compilation with its own artist on each track`() {
        val compilation = AudioFixtures.broken.fixtures.map { it.tags() }.filter { it.compilation }

        assertTrue(compilation.size > 1)
        assertEquals(setOf("Various Artists"), compilation.map { it.albumArtist }.toSet())
        assertEquals(compilation.size, compilation.map { it.artist }.distinct().size)
    }

    @Test
    fun `the broken set has a text file and truncated files named as audio`() {
        val text = AudioFixtures.broken.fixtures.single { it.content is FixtureContent.Text }
        assertTrue(text.fileName.endsWith(".mp3"))
        assertTrue(text.bytes().decodeToString().startsWith("This is a text file"))

        val truncated = AudioFixtures.broken.fixtures.filter { it.content is FixtureContent.Truncated }
        assertEquals(setOf("mp3", "m4a"), truncated.map { it.fileName.substringAfterLast('.') }.toSet())
        truncated.forEach { fixture ->
            val whole = (fixture.content as FixtureContent.Truncated).of.bytes()
            assertTrue(fixture.path, fixture.bytes().size < whole.size)
            assertArrayEquals(whole.copyOf(fixture.bytes().size), fixture.bytes())
        }
    }

    private fun AudioFixture.tags(): Tags = when (val content = content) {
        is FixtureContent.Mp3 -> when (val tags = content.tags) {
            is Mp3Tags.Id3v2 -> tags.tags
            is Mp3Tags.Id3v1 -> tags.tags
            Mp3Tags.None -> Tags()
        }

        is FixtureContent.M4a -> content.tags ?: Tags()

        else -> Tags()
    }
}
