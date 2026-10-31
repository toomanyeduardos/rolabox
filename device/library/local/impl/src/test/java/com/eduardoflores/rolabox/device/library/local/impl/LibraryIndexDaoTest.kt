package com.eduardoflores.rolabox.device.library.local.impl

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.RoomLibraryIndexLocalDataSource
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
            ),
            artist = artistGroup,
            album = AlbumGroup("${album.orEmpty()}|${albumArtist.orEmpty()}", album, year, albumArtistGroup),
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

        val song = index.indexedSongs().getOrNull().orEmpty().single()

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
    fun albums_areSortedByYearThenTitle() = runTest {
        fill(
            write(1, album = "Late", year = 2020),
            write(2, album = "None"),
            write(3, album = "Early b", year = 2000),
            write(4, album = "Early a", year = 2000),
        )

        assertEquals(
            listOf("Early a", "Early b", "Late", "None"),
            index.observeAlbums().first().getOrNull().orEmpty().map { it.title },
        )
    }

    @Test
    fun songs_areSortedByTitle_withAMissingOneLast() = runTest {
        fill(write(1, title = null), write(2, title = "b"), write(3, title = "A"))

        assertEquals(listOf("A", "b", null), index.observeSongs().first().getOrNull().orEmpty().map { it.title })
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
    fun anUpdate_keepsTheSongsId() = runTest {
        fill(write(1, title = "Old"))
        val id = index.indexedSongs().getOrNull().orEmpty().single().id

        fill(write(50, id = id, title = "New"))

        val song = index.indexedSongs().getOrNull().orEmpty().single()
        assertEquals(id, song.id)
        assertEquals(50, song.file.mediaStoreId)
        assertEquals("New", song.metadata.title.value)
    }

    @Test
    fun twoSongs_canSwapTheirMediaStoreIdsAcrossBatches() = runTest {
        fill(write(1), write(2))
        val (first, second) = index.indexedSongs().getOrNull().orEmpty().map { it.id }

        fill(write(2, id = first))
        fill(write(1, id = second))

        assertEquals(
            listOf(first to 2L, second to 1L),
            index.indexedSongs().getOrNull().orEmpty().map { it.id to it.file.mediaStoreId },
        )
    }

    @Test
    fun aRemoval_takesAlbumsAndArtistsNothingUsesAnyMore() = runTest {
        fill(write(1, artist = "A", album = "One"), write(2, artist = "B", album = "Two"))
        val removed = index.indexedSongs().getOrNull().orEmpty().first { it.file.mediaStoreId == 2L }.id

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
    }
}
