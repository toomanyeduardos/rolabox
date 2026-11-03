package com.eduardoflores.rolabox.device.library.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedArtworkRepositoryTest {
    private val index = FakeLibraryIndexLocalDataSource()
    private val artwork = FakeArtworkLocalDataSource()
    private val repository = CachedArtworkRepository(index, artwork)

    private val red = FakeArtworkRepository.solid(RED)
    private val blue = FakeArtworkRepository.solid(BLUE)

    private fun uriOf(id: Long) = "content://media/external/audio/media/$id"

    private fun <T : Any> unknown() = Sourced<T>(null, FieldSource.Unknown)

    /** Indexes the files of [ids], at [dateModified], and returns the reference of each song. */
    private suspend fun index(vararg ids: Long, dateModified: Long = 100): List<ArtworkRef> {
        val existing = index.indexed.associate { it.file.mediaStoreId to it.id }
        val artist = ArtistGroup("artist", "Artist")
        val writes = ids.map { id ->
            SongWrite(
                id = existing[id],
                file = audioFile(id, dateModified = dateModified).file,
                metadata = SongMetadata(
                    unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(),
                ),
                artist = artist,
                album = AlbumGroup("album", "Album", null, artist),
            )
        }
        index.write(IndexBatch(writes))
        return ids.map { id -> ArtworkRef(index.indexed.first { it.file.mediaStoreId == id }.id) }
    }

    @Test
    fun aSongsArtwork_isTheThumbnailOfItsFile() = runTest {
        val (song) = index(1)
        artwork.results[uriOf(1)] = red.right()

        assertEquals(red.right(), repository.loadArtwork(song))
        assertEquals(listOf(uriOf(1)), artwork.loads)
    }

    @Test
    fun aSongWithNoArtwork_hasNone() = runTest {
        val (song) = index(1)

        assertEquals(null.right(), repository.loadArtwork(song))
    }

    @Test
    fun aSongNoLongerInTheLibrary_hasNone_andNothingIsLoaded() = runTest {
        assertEquals(null.right(), repository.loadArtwork(ArtworkRef(SongId(99))))
        assertEquals(emptyList<String>(), artwork.loads)
    }

    @Test
    fun aFileTheSystemWontOpen_isAnError_thatIsTriedAgain() = runTest {
        val (song) = index(1)
        artwork.results[uriOf(1)] = LibraryError.FileUnreadable(uriOf(1)).left()

        assertEquals(LibraryError.FileUnreadable(uriOf(1)).left(), repository.loadArtwork(song))
        artwork.results[uriOf(1)] = red.right()
        assertEquals(red.right(), repository.loadArtwork(song))
    }

    @Test
    fun aSecondLoad_comesFromMemory_withOrWithoutArtwork() = runTest {
        val (covered, bare) = index(1, 2)
        artwork.results[uriOf(1)] = red.right()

        repository.loadArtwork(covered)
        repository.loadArtwork(bare)
        assertEquals(red.right(), repository.loadArtwork(covered))
        assertEquals(null.right(), repository.loadArtwork(bare))

        assertEquals(listOf(uriOf(1), uriOf(2)), artwork.loads)
    }

    @Test
    fun aFileThatChanged_loadsAgain() = runTest {
        val (song) = index(1)
        artwork.results[uriOf(1)] = red.right()
        repository.loadArtwork(song)

        index(1, dateModified = 200)
        artwork.results[uriOf(1)] = blue.right()

        assertEquals(blue.right(), repository.loadArtwork(song))
    }

    @Test
    fun onlyTheLastFewCovers_stayInMemory() = runTest {
        val refs = index(*LongArray(CACHED_ARTWORK + 1) { it + 1L })
        refs.forEach { repository.loadArtwork(it) }
        artwork.loads.clear()

        repository.loadArtwork(refs.last())
        assertEquals(emptyList<String>(), artwork.loads)
        repository.loadArtwork(refs.first())
        assertEquals(listOf(uriOf(1)), artwork.loads)
    }

    @Test
    fun aRefIsKeptByItsSong_soTwoSongsDontShareACover() = runTest {
        val (first, second) = index(1, 2)
        artwork.results[uriOf(1)] = red.right()
        artwork.results[uriOf(2)] = blue.right()

        assertEquals(red, repository.loadArtwork(first).getOrNull())
        assertEquals(blue, repository.loadArtwork(second).getOrNull())
        assertNull(repository.loadArtwork(ArtworkRef(SongId(99))).getOrNull())
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
    }
}
