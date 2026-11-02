package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixture
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixtures
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.library.testing.audio.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {
    private fun group(vararg files: AudioFile): List<SongWrite> = groupSongs(files.map { it.toDraft(null) })

    @Test
    fun namesThatDifferOnlyInCaseOrWhitespace_areOneArtist_withTheSpellingOfMostSongs() {
        val writes = group(
            audioFile(1, artist = "Low  Tides", album = "A"),
            audioFile(2, artist = "low tides", album = "B"),
            audioFile(3, artist = " low tides ", album = "C"),
        )

        assertEquals(1, writes.map { it.artist.key }.distinct().size)
        assertEquals("low tides", writes.first().artist.name)
    }

    @Test
    fun theBeatlesAndBeatles_areTwoArtists_sortedTogether() {
        val (the, plain) = group(audioFile(1, artist = "The Beatles"), audioFile(2, artist = "Beatles"))

        assertFalse(the.artist.key == plain.artist.key)
        assertEquals("beatles", the.artist.sortName)
        assertEquals("beatles", plain.artist.sortName)
    }

    @Test
    fun anArtistCalledThe_sortsByTheirWholeName() {
        assertEquals("the", group(audioFile(1, artist = "The")).single().artist.sortName)
    }

    @Test
    fun anAlbum_isListedUnderItsAlbumArtist_andEachSongKeepsItsArtist() {
        val writes = group(
            audioFile(1, artist = "A", albumArtist = "Label", album = "Hits"),
            audioFile(2, artist = "B", albumArtist = "Label", album = "Hits"),
        )

        assertEquals(1, writes.map { it.album.key }.distinct().size)
        assertEquals("Label", writes.first().album.artist.name)
        assertEquals(listOf("A", "B"), writes.map { it.artist.name })
    }

    @Test
    fun differentTrackArtists_withNoAlbumArtist_areVariousArtists() {
        val writes = group(audioFile(1, artist = "A", album = "Hits"), audioFile(2, artist = "B", album = "Hits"))

        assertEquals(1, writes.map { it.album.key }.distinct().size)
        assertTrue(writes.first().album.artist.isVarious)
        assertNull(writes.first().album.artist.name)
        assertEquals(listOf("A", "B"), writes.map { it.artist.name })
    }

    @Test
    fun theCompilationFlag_isVariousArtists_evenWithOneArtist() {
        assertTrue(group(audioFile(1, artist = "A", compilation = true)).single().album.artist.isVarious)
    }

    @Test
    fun aTagOfVariousArtists_isTheSameRow_whateverItsSpelling() {
        val writes = group(
            audioFile(1, artist = "A", albumArtist = "VARIOUS ARTISTS", album = "One"),
            audioFile(2, artist = "A", albumArtist = "Various", album = "Two"),
            audioFile(3, artist = "B", compilation = true, album = "Three"),
        )

        assertEquals(1, writes.map { it.album.artist.key }.distinct().size)
        assertTrue(writes.all { it.album.artist.isVarious })
    }

    @Test
    fun aSongTaggedVariousArtists_hasNoArtistOfItsOwn() {
        val write = group(audioFile(1, artist = "Various Artists", album = "Hits")).single()

        assertNull(write.artist.name)
        assertFalse(write.artist.isVarious)
        assertTrue(write.album.artist.isVarious)
    }

    @Test
    fun twoAlbumsOfOneName_byTwoAlbumArtists_areTwoAlbums() {
        val writes = group(
            audioFile(1, artist = "A", albumArtist = "A", album = "Greatest Hits"),
            audioFile(2, artist = "B", albumArtist = "B", album = "Greatest Hits"),
        )

        assertEquals(2, writes.map { it.album.key }.distinct().size)
    }

    @Test
    fun twoUntaggedAlbumsOfOneName_inTwoFolders_areTwoAlbums() {
        val writes = group(
            audioFile(1, path = "Music/A/Hits/", artist = "A", album = "Hits"),
            audioFile(2, path = "Music/B/Hits/", artist = "B", album = "Hits"),
        )

        assertEquals(listOf("A", "B"), writes.map { it.album.artist.name })
    }

    @Test
    fun oneArtistsSongs_areOneAlbum_whateverTheirFolders() {
        val writes = group(
            audioFile(1, path = "Music/A/Hits/", artist = "A", albumArtist = "A", album = "Hits"),
            audioFile(2, path = "Music/Dump/", artist = "A", album = "hits"),
        )

        assertEquals(1, writes.map { it.album.key }.distinct().size)
    }

    @Test
    fun songsWithNoAlbum_areOneRow_whoeverTheirArtists() {
        val writes = group(
            audioFile(1, path = "Music/", artist = "A", album = null),
            audioFile(2, path = "Music/", artist = "B", album = null),
            audioFile(3, path = "Music/X/", artist = null, album = null),
        )

        assertEquals(1, writes.map { it.album.key }.distinct().size)
        assertNull(writes.first().album.title)
        assertNull(writes.first().album.artist.name)
        assertEquals(listOf("A", "B", null), writes.map { it.artist.name })
    }

    @Test
    fun theSameSongTwice_showsTheLargestFile() {
        val writes = group(
            audioFile(1, path = "Music/Downloads/", size = 100, title = "Song", artist = "A", album = "Hits"),
            audioFile(2, path = "Music/A/Hits/", size = 200, title = "song", artist = "a", album = "HITS"),
        )

        assertEquals(listOf(true, false), writes.map { it.isDuplicate })
    }

    @Test
    fun copiesOfOneSize_showTheOldestRow() {
        val song = { id: Long -> audioFile(id, size = 100, title = "Song", artist = "A", album = "Hits") }
        val drafts = listOf(song(1).toDraft(null).copy(id = SongId(9)), song(2).toDraft(null).copy(id = SongId(5)))

        assertEquals(listOf(true, false), groupSongs(drafts).map { it.isDuplicate })
    }

    @Test
    fun sameTags_withAnotherDuration_orOnAnotherAlbum_areTwoSongs() {
        val song = audioFile(1, title = "Song", artist = "A", album = "Hits")
        val longer = audioFile(2, title = "Song", artist = "A", album = "Hits", durationMillis = 2000)
        val elsewhere = audioFile(3, title = "Song", artist = "A", album = "Live")

        assertTrue(group(song, longer, elsewhere).none { it.isDuplicate })
    }

    @Test
    fun filesWithNoTitleOfTheirOwn_areNeverDuplicates() {
        val writes = group(
            audioFile(1, path = "Music/X/", name = "Same.mp3", title = null, artist = null, album = null),
            audioFile(2, path = "Music/Y/", name = "Same.mp3", title = null, artist = null, album = null),
        )

        assertTrue(writes.none { it.isDuplicate })
    }

    @Test
    fun keysStayTheSame_whenTheLibraryGrows() {
        val first = group(audioFile(1, artist = "A", album = "Hits"))
        val second = group(audioFile(1, artist = "A", album = "Hits"), audioFile(2, artist = "A", album = "Hits"))

        assertEquals(first.single().album.key, second.first().album.key)
        assertEquals(first.single().artist.key, second.first().artist.key)
    }

    @Test
    fun theBrokenSet_hasNoArtistOrAlbumTwice_andNoAlbumSplit() {
        val writes = groupSongs(AudioFixtures.broken.fixtures.mapIndexed(::audioFileOf).map { it.toDraft(null) })

        val shown = writes.filterNot { it.isDuplicate }
        val artists = shown.flatMap { listOf(it.artist, it.album.artist) }.distinct()
        assertEquals(artists.size, artists.map { it.name to it.isVarious }.distinct().size)
        val albums = shown.map { it.album }.distinct()
        assertEquals(albums.map { it.key }.distinct().size, albums.size)

        val titled = { title: String -> albums.filter { it.title == title } }
        assertEquals(2, titled("Greatest Hits").size)
        assertTrue(titled("Summer Mixtape").single().artist.isVarious)
        assertEquals(1, titled("Wrong Numbers").size)
        assertEquals(1, shown.count { it.metadata.title.value == "Glass Harbor" })
        assertEquals(1, writes.count { it.isDuplicate })
    }

    /** What MediaStore would list for a fixture. The tags are the fixture's, since the parser has its own tests. */
    private fun audioFileOf(index: Int, fixture: AudioFixture): AudioFile {
        val tags = tagsOf(fixture.content)
        // The same bytes are the same audio, so the length stands in for the duration.
        val length = fixture.bytes().size.toLong()
        val folder = "Music/${fixture.path.substringBeforeLast('/')}/"
        return AudioFile(
            file = SongFile(index.toLong(), "content://$index", folder, fixture.fileName, length, DATE),
            title = tags?.title,
            artist = tags?.artist,
            albumArtist = tags?.albumArtist,
            album = tags?.album,
            trackNumber = tags?.track?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 },
            discNumber = tags?.disc?.substringBefore('/')?.toIntOrNull(),
            year = tags?.year?.toIntOrNull(),
            compilation = tags?.compilation?.takeIf { it },
            durationMillis = length,
        )
    }

    private fun tagsOf(content: FixtureContent): Tags? = when (content) {
        is FixtureContent.Mp3 -> when (val tags = content.tags) {
            Mp3Tags.None -> null
            is Mp3Tags.Id3v1 -> tags.tags
            is Mp3Tags.Id3v2 -> tags.tags
        }

        is FixtureContent.M4a -> content.tags

        is FixtureContent.Truncated -> tagsOf(content.of)

        else -> null
    }

    private companion object {
        const val DATE = 100L
    }
}
