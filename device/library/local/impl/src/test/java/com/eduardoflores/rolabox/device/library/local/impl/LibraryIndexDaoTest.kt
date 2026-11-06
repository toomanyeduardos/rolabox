package com.eduardoflores.rolabox.device.library.local.impl

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.GenreGroup
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.RoomLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.tags.ArrayByteSource
import com.eduardoflores.rolabox.device.library.local.impl.tags.parseTags
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixtureSet
import com.eduardoflores.rolabox.device.library.testing.audio.AudioFixtures
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryIndexDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        LibraryDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao = database.libraryIndexDao()
    private val index = RoomLibraryIndexLocalDataSource(dao, UnconfinedTestDispatcher())

    @After
    fun closeDatabase() = database.close()

    private fun <T : Any> known(value: T?) =
        Sourced(value, if (value == null) FieldSource.Unknown else FieldSource.MediaStore)

    @Suppress("LongParameterList") // One parameter per thing a test can vary.
    private fun write(
        mediaStoreId: Long,
        id: SongId? = null,
        title: String? = "Song $mediaStoreId",
        artist: String? = "Artist",
        albumArtist: String? = artist,
        album: String? = "Album",
        year: Int? = null,
        track: Int? = null,
        disc: Int? = null,
        duration: Long? = 1000,
        picture: Boolean? = null,
        genres: List<String?> = listOf(null),
    ): SongWrite {
        val artistGroup = ArtistGroup(artist.orEmpty(), artist)
        val albumArtistGroup = ArtistGroup(albumArtist.orEmpty(), albumArtist)
        return SongWrite(
            id = id,
            file = SongFile(mediaStoreId, "content://song/$mediaStoreId", "Music/", "$mediaStoreId.mp3", 1, 2),
            metadata = SongMetadata(
                title = known(title),
                artist = known(artist),
                albumArtist = known(albumArtist),
                album = known(album),
                trackNumber = known(track),
                discNumber = known(disc),
                year = known(year),
                compilation = known(null),
                durationMillis = known(duration),
                genres = known(genres.filterNotNull().ifEmpty { null }),
            ),
            artist = artistGroup,
            album = AlbumGroup("${album.orEmpty()}|${albumArtist.orEmpty()}", album, year, albumArtistGroup),
            hasEmbeddedPicture = picture,
            // A genre's key is its name in lower case, and no name is Unknown Genre's.
            genres = genres.map { GenreGroup(it?.lowercase().orEmpty(), it) },
        )
    }

    private suspend fun fill(vararg writes: SongWrite) {
        index.write(IndexBatch(writes = writes.toList()))
    }

    private suspend fun artistId(name: String?): ArtistId =
        index.observeArtists().first().getOrNull().orEmpty().first { it.name == name }.id

    @Test
    fun aSong_comesBackWithWhatTheApiShows() = runTest {
        fill(write(1, title = "Song", track = 3, duration = 2000))

        val song = index.observeSongs().first().getOrNull().orEmpty().single()

        assertEquals("Song", song.title)
        assertEquals(3, song.trackNumber)
        assertEquals(2000.milliseconds, song.duration)
        assertEquals("content://song/1", song.uri)
    }

    @Test
    fun aSongWithNoDuration_isZeroLong() = runTest {
        fill(write(1, duration = null))

        assertEquals(Duration.ZERO, index.observeSongs().first().getOrNull().orEmpty().single().duration)
    }

    @Test
    fun aSongAsIndexed_keepsEachValueWithItsSource_andItsKeys() = runTest {
        val written = write(1, title = null, year = 2001)
        fill(written)

        val song = index.getIndexedSongs().getOrNull().orEmpty().single()

        assertEquals(written.file, song.file)
        assertEquals(written.metadata, song.metadata)
        assertEquals(FieldSource.Unknown, song.metadata.title.source)
        assertEquals(written.artist.key, song.artistKey)
        assertEquals(written.album.key, song.albumKey)
    }

    @Test
    fun aMissingTitle_isStoredAsNoValue() = runTest {
        fill(write(1, title = null))

        assertNull(index.observeSongs().first().getOrNull().orEmpty().single().title)
    }

    @Test
    fun artists_areSortedByName_withAMissingOneLast() = runTest {
        fill(
            write(1, artist = null),
            write(2, artist = "bravo"),
            write(3, artist = "Alpha"),
            write(4, artist = "Charlie"),
        )

        assertEquals(
            listOf("Alpha", "bravo", "Charlie", null),
            index.observeArtists().first().getOrNull().orEmpty().map { it.name },
        )
    }

    @Test
    fun albums_areSortedByTitle_withoutALeadingThe_andTheUnknownOneLast() = runTest {
        val unknown = AlbumGroup("none", title = null, year = null, artist = ArtistGroup("", null))
        fill(
            write(1, album = "Late", year = 2000),
            write(2, album = null).copy(album = unknown),
            write(3, album = "the Wall", year = 2020),
            write(4, album = "early", year = 2020),
            write(5, album = "The"),
            write(6, album = "Theory"),
        )

        assertEquals(
            listOf("early", "Late", "The", "Theory", "the Wall", null),
            index.observeAlbums().first().getOrNull().orEmpty().map { it.title },
        )
    }

    @Test
    fun songs_areSortedByTitle_withoutALeadingThe_andAMissingOneLast() = runTest {
        fill(write(1, title = null), write(2, title = "b"), write(3, title = "A"), write(4, title = "The Air"))

        assertEquals(
            listOf("A", "The Air", "b", null),
            index.observeSongs().first().getOrNull().orEmpty().map { it.title },
        )
    }

    @Test
    fun theSongsOfAnAlbum_followTheDiscAndTrackOrder() = runTest {
        fill(
            write(1, title = "d2t1", disc = 2, track = 1),
            write(2, title = "none"),
            write(3, title = "d1t2", disc = 1, track = 2),
            write(4, title = "d1t1", disc = 1, track = 1),
        )
        val album = index.observeAlbums().first().getOrNull().orEmpty().single()

        assertEquals(
            listOf("d1t1", "d1t2", "d2t1", "none"),
            index.observeSongsByAlbum(album.id).first().getOrNull().orEmpty().map { it.title },
        )
    }

    @Test
    fun anArtist_hasTheirAlbums_andThoseOfOthersTheyHaveASongOn() = runTest {
        fill(
            write(1, artist = "A", album = "Own"),
            write(2, artist = "A", albumArtist = "Various", album = "Hits"),
            write(3, artist = "B", albumArtist = "Various", album = "Hits"),
            write(4, artist = "B", album = "Other"),
        )

        assertEquals(
            setOf("Own", "Hits"),
            index.observeAlbumsByArtist(artistId("A")).first().getOrNull().orEmpty().map { it.title }.toSet(),
        )
    }

    @Test
    fun anAlbumArtist_hasTheSongsOfTheirAlbums() = runTest {
        fill(
            write(1, artist = "A", albumArtist = "Various", album = "Hits"),
            write(2, artist = "B", albumArtist = "Various", album = "Hits"),
            write(3, artist = "B", album = "Other"),
        )

        assertEquals(2, index.observeSongsByArtist(artistId("Various")).first().getOrNull().orEmpty().size)
        assertEquals(2, index.observeSongsByArtist(artistId("B")).first().getOrNull().orEmpty().size)
    }

    @Test
    fun anAlbumOrArtistKey_keepsItsId_acrossWrites() = runTest {
        fill(write(1))
        val artist = artistId("Artist")
        val album = index.observeAlbums().first().getOrNull().orEmpty().single().id

        fill(write(2))

        assertEquals(artist, artistId("Artist"))
        assertEquals(album, index.observeAlbums().first().getOrNull().orEmpty().single().id)
    }

    @Test
    fun artists_sortByTheirSortName_withUnknownLast() = runTest {
        fill(
            write(1, artist = null, album = "One"),
            write(2, artist = "Zed", album = "Two"),
            write(3, artist = "Abba", album = "Three").let {
                it.copy(artist = ArtistGroup("the beatles", "The Beatles", sortName = "beatles"))
            },
        )

        assertEquals(
            listOf("The Beatles", "Zed", null),
            index.observeArtists().first().getOrNull().orEmpty().map { it.name }.filterNot { it == "Abba" },
        )
    }

    @Test
    fun aDuplicate_isInNoList_andStaysIndexed() = runTest {
        fill(write(1, title = "Shown"), write(2, title = "Copy").copy(isDuplicate = true))

        assertEquals(listOf("Shown"), index.observeSongs().first().getOrNull().orEmpty().map { it.title })
        assertEquals(1, index.observeSongsByArtist(artistId("Artist")).first().getOrNull().orEmpty().size)
        val album = index.observeAlbums().first().getOrNull().orEmpty().single().id
        assertEquals(1, index.observeSongsByAlbum(album).first().getOrNull().orEmpty().size)
        assertEquals(2, index.getIndexedSongs().getOrNull().orEmpty().size)
    }

    @Test
    fun theUnknownAlbum_isOneRow_onNoArtistsScreen_withTheirSongsThere() = runTest {
        val unknown = AlbumGroup("none", title = null, year = null, artist = ArtistGroup("", null))
        fill(
            write(1, artist = "A", album = null).copy(album = unknown),
            write(2, artist = "B", album = null).copy(album = unknown),
        )

        assertEquals(listOf(null), index.observeAlbums().first().getOrNull().orEmpty().map { it.title })
        assertEquals(emptyList<Any>(), index.observeAlbumsByArtist(artistId("A")).first().getOrNull().orEmpty())
        assertEquals(1, index.observeSongsByArtist(artistId("A")).first().getOrNull().orEmpty().size)
        // Nobody's song is the unknown artist's, so they aren't a row.
        assertEquals(listOf("A", "B"), index.observeArtists().first().getOrNull().orEmpty().map { it.name })
    }

    @Test
    fun aNewSpelling_changesTheRow_andKeepsItsId() = runTest {
        fill(write(1, artist = "abba"))
        val id = artistId("abba")

        fill(write(2, artist = "abba", albumArtist = "Other").copy(artist = ArtistGroup("abba", "ABBA")))

        assertEquals(id, artistId("ABBA"))
    }

    @Test
    fun anUpdate_keepsTheSongsId() = runTest {
        fill(write(1, title = "Old"))
        val id = index.getIndexedSongs().getOrNull().orEmpty().single().id

        fill(write(50, id = id, title = "New"))

        val song = index.getIndexedSongs().getOrNull().orEmpty().single()
        assertEquals(id, song.id)
        assertEquals(50, song.file.mediaStoreId)
        assertEquals("New", song.metadata.title.value)
    }

    @Test
    fun twoSongs_canSwapTheirMediaStoreIdsAcrossBatches() = runTest {
        fill(write(1), write(2))
        val (first, second) = index.getIndexedSongs().getOrNull().orEmpty().map { it.id }

        fill(write(2, id = first))
        fill(write(1, id = second))

        assertEquals(
            listOf(first to 2L, second to 1L),
            index.getIndexedSongs().getOrNull().orEmpty().map { it.id to it.file.mediaStoreId },
        )
    }

    @Test
    fun aRemoval_takesAlbumsAndArtistsNothingUsesAnyMore() = runTest {
        fill(write(1, artist = "A", album = "One"), write(2, artist = "B", album = "Two"))
        val removed = index.getIndexedSongs().getOrNull().orEmpty().first { it.file.mediaStoreId == 2L }.id

        index.write(IndexBatch(removals = listOf(removed)))

        assertEquals(listOf("A"), index.observeArtists().first().getOrNull().orEmpty().map { it.name })
        assertEquals(listOf("One"), index.observeAlbums().first().getOrNull().orEmpty().map { it.title })
    }

    @Test
    fun anAlbumArtist_staysWhileTheirAlbumHasSongs_evenWithNoSongOfTheirOwn() = runTest {
        fill(write(1, artist = "A", albumArtist = "Various", album = "Hits"))

        assertEquals(
            listOf("A", "Various"),
            index.observeArtists().first().getOrNull().orEmpty().map { it.name },
        )
    }

    @Test
    fun aSongWithNoYear_doesNotTakeTheYearFromItsAlbum() = runTest {
        fill(write(1, year = 2001))

        fill(write(2, year = null))

        assertEquals(2001, index.observeAlbums().first().getOrNull().orEmpty().single().year)
    }

    @Test
    fun theSongCount_followsTheWrites() = runTest {
        assertEquals(0, index.observeSongCount().first().getOrNull())

        fill(write(1), write(2))

        assertEquals(2, index.observeSongCount().first().getOrNull())
    }

    @Test
    fun theScanState_isReplaced() = runTest {
        index.saveScanState(listOf(VolumeScanState("old", "v1", 1), VolumeScanState("external_primary", "v1", null)))

        index.saveScanState(listOf(VolumeScanState("external_primary", "v2", 9)))

        val saved = dao.scanState().single()
        assertEquals("external_primary", saved.volume)
        assertEquals("v2", saved.version)
        assertEquals(9L, saved.generation)
        assertEquals(listOf(VolumeScanState("external_primary", "v2", 9)), index.getScanState().getOrNull())
    }

    @Test
    fun theLocations_includeASongNoListShows() = runTest {
        fill(write(1, title = "Shown"), write(2, title = "Copy").copy(isDuplicate = true))

        val locations = index.observeSongLocations().first().getOrNull().orEmpty()
        val songs = index.getIndexedSongs().getOrNull().orEmpty()

        assertEquals(songs.associate { it.id to it.file.uri }, locations)
        assertEquals(2, locations.size)
    }

    // The cover of an album (ADR-026), with whether a song has a picture as the parser reads library/02's fixtures.

    private fun hasPicture(set: AudioFixtureSet, name: String): Boolean? =
        parseTags(ArrayByteSource(set.fixtures.first { it.path.endsWith(name) }.bytes()))?.hasEmbeddedPicture

    private suspend fun theAlbum() = index.observeAlbums().first().getOrNull().orEmpty().single()

    @Test
    fun anAlbumsArtwork_isItsFirstSongWithAPicture_inTrackOrder() = runTest {
        val bare = hasPicture(AudioFixtures.broken, "05 Static Bloom.mp3")
        val covered = hasPicture(AudioFixtures.clean, ".mp3")
        assertEquals(false, bare)
        assertEquals(true, covered)
        fill(
            write(1, track = 1, picture = bare),
            write(2, track = 3, picture = covered),
            write(3, track = 2, picture = covered),
        )

        assertEquals(ArtworkRef(SongId(3)), theAlbum().artwork)
    }

    @Test
    fun anAlbumWithNoPicture_hasTheArtworkOfItsFirstSong_whoseFolderMayHaveOne() = runTest {
        fill(write(1, track = 2, picture = false), write(2, track = 1), write(3, track = 3, picture = false))

        assertEquals(ArtworkRef(SongId(2)), theAlbum().artwork)
    }

    @Test
    fun anAlbumsArtwork_isTheSameWhereverTheAlbumIsRead() = runTest {
        fill(write(1, track = 2, picture = true), write(2, track = 1))
        val album = theAlbum()

        assertEquals(ArtworkRef(SongId(1)), album.artwork)
        assertEquals(album, index.getAlbum(album.id).getOrNull())
        assertEquals(album, index.observeAlbum(album.id).first().getOrNull())
        assertEquals(listOf(album), index.observeAlbumsByArtist(album.artistId).first().getOrNull())
    }

    @Test
    fun aSongsFile_isFoundByItsId_andASongNotIndexedHasNone() = runTest {
        fill(write(7))
        val song = index.getIndexedSongs().getOrNull().orEmpty().single()

        assertEquals(song.file, index.getSongFile(song.id).getOrNull())
        assertNull(index.getSongFile(SongId(99)).getOrNull())
    }

    // Genres (ADR-024, Genre)

    private suspend fun genres() = index.observeGenres().first().getOrNull().orEmpty()

    private suspend fun genreId(name: String?) = genres().first { it.name == name }.id

    @Test
    fun genres_areByName_withUnknownGenreLast() = runTest {
        fill(write(1, genres = listOf(null)), write(2, genres = listOf("rock")), write(3, genres = listOf("Ambient")))

        assertEquals(listOf("Ambient", "rock", null), genres().map { it.name })
    }

    @Test
    fun aSongWithSeveralGenres_isInEach() = runTest {
        fill(write(1, genres = listOf("Rock", "Jazz")), write(2, genres = listOf("Rock")))

        val inJazz = index.observeSongsByGenreAndArtist(genreId("Jazz"), artistId("Artist")).first().getOrNull()
        val inRock = index.observeSongsByGenreAndArtist(genreId("Rock"), artistId("Artist")).first().getOrNull()
        assertEquals(listOf("Song 1"), inJazz.orEmpty().map { it.title })
        assertEquals(listOf("Song 1", "Song 2"), inRock.orEmpty().map { it.title })
    }

    @Test
    fun aSongsGenres_areStoredWithTheirSource() = runTest {
        val written = write(1, genres = listOf("Rock", "Jazz"))
        fill(written)

        assertEquals(written.metadata.genres, index.getIndexedSongs().getOrNull().orEmpty().single().metadata.genres)
    }

    @Test
    fun aGenre_isTheArtistsOfItsSongs_andOfTheirAlbums() = runTest {
        fill(
            write(1, artist = "A", albumArtist = "Label", album = "Hits", genres = listOf("Rock")),
            write(2, artist = "B", album = "Other", genres = listOf("Jazz")),
        )

        val artists = index.observeArtistsByGenre(genreId("Rock")).first().getOrNull().orEmpty()
        assertEquals(listOf("A", "Label"), artists.map { it.name })
    }

    @Test
    fun anArtistInAGenre_hasOnlyTheAlbumsWithASongInIt() = runTest {
        fill(
            write(1, album = "Loud", genres = listOf("Rock")),
            write(2, album = "Quiet", genres = listOf("Jazz")),
            write(3, album = "Both", genres = listOf("Rock")),
            write(4, album = "Both", genres = listOf("Jazz")),
        )

        val albums = index.observeAlbumsByGenreAndArtist(genreId("Rock"), artistId("Artist")).first().getOrNull()
        assertEquals(setOf("Loud", "Both"), albums.orEmpty().map { it.title }.toSet())
    }

    @Test
    fun anAlbumInAGenre_isOnlyItsSongsInIt_inTrackOrder() = runTest {
        fill(
            write(1, track = 2, genres = listOf("Rock")),
            write(2, track = 1, genres = listOf("Rock")),
            write(3, track = 3, genres = listOf("Jazz")),
        )
        val album = index.observeAlbums().first().getOrNull().orEmpty().single()

        val songs = index.observeSongsByGenreAndAlbum(genreId("Rock"), album.id).first().getOrNull().orEmpty()
        assertEquals(listOf("Song 2", "Song 1"), songs.map { it.title })
    }

    @Test
    fun aGenreLeftWithNoSong_isGone_andARewrittenSongLeavesItsOldGenres() = runTest {
        fill(write(1, genres = listOf("Rock")), write(2, genres = listOf("Jazz")))
        val rewritten = write(1, id = index.getIndexedSongs().getOrNull().orEmpty().first().id, genres = listOf("Pop"))

        index.write(IndexBatch(writes = listOf(rewritten), removals = listOf(SongId(2))))

        assertEquals(listOf("Pop"), genres().map { it.name })
    }

    @Test
    fun aGenreKeepsItsId_whileItsSpellingFollowsTheSongs() = runTest {
        fill(write(1, genres = listOf("rock")))
        val before = genreId("rock")

        fill(write(2, genres = listOf("Rock")))

        assertEquals(listOf(GenreLike(before, "Rock")), genres().map { GenreLike(it.id, it.name) })
    }

    private data class GenreLike(val id: Any, val name: String?)
}
