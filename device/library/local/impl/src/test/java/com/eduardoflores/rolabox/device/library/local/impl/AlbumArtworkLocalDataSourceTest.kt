package com.eduardoflores.rolabox.device.library.local.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.testing.FakePreferencesStore
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource.Companion.ARTWORK_SIZE
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import com.eduardoflores.rolabox.device.library.local.impl.artwork.RoomAlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.artwork.sampleSizeFor
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.RoomLibraryIndexLocalDataSource
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
// Real decoding, so a file that isn't an image decodes to nothing, as it does on a phone.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class AlbumArtworkLocalDataSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val index = RoomLibraryIndexLocalDataSource(database.libraryIndexDao(), UnconfinedTestDispatcher())
    private val folder = File(context.filesDir, "artwork-test").apply { deleteRecursively() }
    private val preferences = FakePreferencesStore()
    private val artwork =
        RoomAlbumArtworkLocalDataSource(database.albumArtworkDao(), folder, preferences, UnconfinedTestDispatcher())

    @After
    fun close() {
        database.close()
        folder.deleteRecursively()
    }

    private fun <T : Any> known(value: T?) =
        Sourced(value, if (value == null) FieldSource.Unknown else FieldSource.Tags)

    private fun song(
        id: Long,
        album: String?,
        artist: ArtistGroup,
        track: Int? = id.toInt(),
        picture: Boolean? = false,
        songArtist: String? = artist.name,
        year: Int? = null,
    ) = SongWrite(
        id = null,
        file = SongFile(id, "content://song/$id", "Music/", "$id.m4a", 1, 2),
        metadata = SongMetadata(
            title = known("Song $id"),
            artist = known(songArtist),
            albumArtist = known(artist.name),
            album = known(album),
            trackNumber = known(track),
            discNumber = known(null),
            year = known(year),
            compilation = known(null),
            durationMillis = known(1000L),
        ),
        artist = artist,
        album = AlbumGroup("${album.orEmpty()}|${artist.key}", album, null, artist),
        hasEmbeddedPicture = picture,
    )

    private val coldplay = ArtistGroup("coldplay", "Coldplay")

    @Test
    fun anAlbum_comesWithItsNames_andTheFileOfTheSongThatStandsForIt() = runTest {
        index.write(
            IndexBatch(
                listOf(
                    song(1, "Live 2012", coldplay, track = 2),
                    song(2, "Live 2012", coldplay, track = 1),
                ),
            ),
        )

        assertEquals(
            listOf(
                ArtworkAlbum(
                    "Live 2012|coldplay",
                    "Live 2012",
                    "Coldplay",
                    false,
                    "content://song/2",
                    null,
                    songArtists = listOf("Coldplay"),
                    highestTrackNumber = 2,
                ),
            ).right(),
            artwork.getAlbums(),
        )
    }

    // What the match reads of the songs (ADR-027, How a match is judged).
    @Test
    fun anAlbum_comesWithItsSongsArtists_highestTrack_andTheYearTheyShare() = runTest {
        val dmb = ArtistGroup("dave matthews", "Dave Matthews")
        val duo = "Dave Matthews & Tim Reynolds"
        val hendrix = ArtistGroup("jimi hendrix", "Jimi Hendrix")
        index.write(
            IndexBatch(
                listOf(
                    song(1, "Radio City", dmb, track = 3, songArtist = duo, year = 2007),
                    song(2, "Radio City", dmb, track = 14, songArtist = duo, year = 2007),
                    song(3, "Radio City", dmb, track = null, songArtist = "Dave Matthews", year = null),
                    song(4, "Experience", hendrix, track = 20, year = 1967),
                    song(5, "Experience", hendrix, track = 4, year = 1971),
                ),
            ),
        )

        val albums = artwork.getAlbums().getOrNull().orEmpty().associateBy { it.title }
        val radioCity = checkNotNull(albums["Radio City"])
        assertEquals(setOf(duo, "Dave Matthews"), radioCity.songArtists.toSet())
        assertEquals(14, radioCity.highestTrackNumber)
        assertEquals(2007, radioCity.year)
        val experience = checkNotNull(albums["Experience"])
        assertEquals(20, experience.highestTrackNumber)
        // Its songs carry the years they were recorded: no single year.
        assertEquals(null, experience.year)
    }

    @Test
    fun variousArtists_andTheUnknownArtist_haveNoName() = runTest {
        index.write(
            IndexBatch(
                listOf(
                    song(1, "Hits", ArtistGroup("various", null, isVarious = true)),
                    song(2, null, ArtistGroup("", null)),
                ),
            ),
        )

        val albums = artwork.getAlbums().getOrNull().orEmpty()
        assertEquals(listOf(true to null, false to null), albums.map { it.isVarious to it.artistName })
        assertNull(albums[1].title)
    }

    @Test
    fun aRecord_isKeptByTheAlbumsKey_andCanBeObserved() = runTest {
        index.write(IndexBatch(listOf(song(1, "Live 2012", coldplay))))

        artwork.setRecord("Live 2012|coldplay", ArtworkRecord.NotFound(2, 99))

        assertEquals(ArtworkRecord.NotFound(2, 99).right(), artwork.observeRecord("Live 2012|coldplay").first())
        assertEquals(ArtworkRecord.NotFound(2, 99), artwork.getAlbum("Live 2012|coldplay").getOrNull()?.record)
        assertEquals(ArtworkRecord.NotFound(2, 99), artwork.getAlbumOfSong(SongId(1)).getOrNull()?.record)
        assertNull(artwork.getAlbumOfSong(SongId(42)).getOrNull())
    }

    @Test
    fun aRecord_survivesTheAlbumLeavingTheIndex() = runTest {
        index.write(IndexBatch(listOf(song(1, "Live 2012", coldplay))))
        artwork.setRecord("Live 2012|coldplay", ArtworkRecord.OnPhone)

        index.write(IndexBatch(removals = listOf(SongId(1))))

        assertEquals(emptyList<ArtworkAlbum>().right(), artwork.getAlbums())
        assertEquals(ArtworkRecord.OnPhone.right(), artwork.observeRecord("Live 2012|coldplay").first())
    }

    @Test
    fun aSavedCover_isAWholeFile_thatTheRecordNames_andLoadsAsAnImage() = runTest {
        val bytes = png(Color.BLUE, 40)

        artwork.saveCover("key", "mbid", bytes, foundAt = 7)

        val record = artwork.observeRecord("key").first().getOrNull() as ArtworkRecord.Found
        assertEquals("mbid" to 7L, record.sourceId to record.foundAt)
        assertEquals(listOf(record.fileName), folder.list().orEmpty().toList())
        val image = checkNotNull(artwork.loadCover(record.fileName).getOrNull())
        assertEquals(40 to 40, image.width to image.height)
        assertEquals(Color.BLUE, image.pixels[0])
    }

    @Test
    fun aCoverThatIsGone_orIsntAnImage_loadsAsNone() = runTest {
        assertEquals(null.right(), artwork.loadCover("gone.jpg"))

        folder.mkdirs()
        File(folder, "broken.jpg").writeText("<html>")
        assertEquals(null.right(), artwork.loadCover("broken.jpg"))
    }

    @Test
    fun aDeletedCover_isGone_andDeletingItAgainIsFine() = runTest {
        artwork.saveCover("key", "mbid", png(Color.RED, 4), foundAt = 1)
        val fileName = (artwork.observeRecord("key").first().getOrNull() as ArtworkRecord.Found).fileName

        assertEquals(Unit.right(), artwork.deleteCover(fileName))
        assertFalse(File(folder, fileName).exists())
        assertEquals(Unit.right(), artwork.deleteCover(fileName))
    }

    @Test
    fun downloading_isOnUntilTheUserTurnsItOff() = runTest {
        assertEquals(true.right(), artwork.observeDownloadEnabled().first())

        artwork.setDownloadEnabled(false)
        assertEquals(false.right(), artwork.observeDownloadEnabled().first())

        artwork.setDownloadEnabled(true)
        assertTrue(artwork.observeDownloadEnabled().first().getOrNull()!!)
    }

    @Test
    fun aLargeCover_isSampledDownToTheSizeOfTheThumbnails() {
        assertEquals(1, sampleSizeFor(500))
        assertEquals(1, sampleSizeFor(ARTWORK_SIZE * 2 - 1))
        assertEquals(2, sampleSizeFor(ARTWORK_SIZE * 2))
        assertEquals(4, sampleSizeFor(ARTWORK_SIZE * 4 + 10))
    }

    private fun png(color: Int, side: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
