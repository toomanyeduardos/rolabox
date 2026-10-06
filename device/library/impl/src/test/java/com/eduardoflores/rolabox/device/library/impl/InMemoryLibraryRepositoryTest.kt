package com.eduardoflores.rolabox.device.library.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLibraryRepositoryTest {
    private val repository = InMemoryLibraryRepository()

    @Test
    fun observeArtists_hasSeveralSortedByName() = runTest {
        val artists = artists()

        assertTrue(artists.size >= 5)
        assertEquals(artists.map { it.name }.sorted(), artists.map { it.name })
    }

    @Test
    fun observeSongs_isLongEnoughToScroll() = runTest {
        assertTrue(songs().size >= 50)
    }

    @Test
    fun observeAlbumsByArtist_anArtistHasSeveralAlbumsInYearOrder() = runTest {
        val albums = albums()
            .groupBy { it.artistId }
            .values
            .maxBy { it.size }
        val artistId = albums.first().artistId

        val observed = repository.observeAlbumsByArtist(artistId).first().getOrNull().orEmpty()

        assertTrue(observed.size >= 3)
        assertEquals(albums.sortedBy { it.year }, observed)
    }

    @Test
    fun observeAlbumsByArtist_returnsOnlyThatArtistsAlbums() = runTest {
        val artistId = artists().first().id

        val albums = repository.observeAlbumsByArtist(artistId).first().getOrNull().orEmpty()

        assertTrue(albums.isNotEmpty())
        assertTrue(albums.all { it.artistId == artistId })
    }

    @Test
    fun observeAlbumsByArtist_unknownArtist_isNotFoundAndEnds() = runTest {
        val unknown = ArtistId(UNKNOWN_ID)

        assertEquals(
            listOf(LibraryError.ArtistNotFound(unknown).left()),
            repository.observeAlbumsByArtist(unknown).toList(),
        )
    }

    @Test
    fun observeSongsByAlbum_followsTrackOrder() = runTest {
        val album = albums().first()

        val songs = repository.observeSongsByAlbum(album.id).first().getOrNull().orEmpty()

        assertTrue(songs.isNotEmpty())
        assertTrue(songs.all { it.albumId == album.id && it.artistId == album.artistId })
        assertEquals((1..songs.size).toList(), songs.map { it.trackNumber })
    }

    @Test
    fun observeSongsByAlbum_unknownAlbum_isNotFoundAndEnds() = runTest {
        val unknown = AlbumId(UNKNOWN_ID)

        assertEquals(
            listOf(LibraryError.AlbumNotFound(unknown).left()),
            repository.observeSongsByAlbum(unknown).toList(),
        )
    }

    @Test
    fun getArtist_known_isReturned() = runTest {
        val artist = artists().first()

        assertEquals(artist.right(), repository.getArtist(artist.id))
    }

    @Test
    fun getArtist_unknown_isNotFound() = runTest {
        val unknown = ArtistId(UNKNOWN_ID)

        assertEquals(LibraryError.ArtistNotFound(unknown).left(), repository.getArtist(unknown))
    }

    @Test
    fun getAlbum_known_isReturned() = runTest {
        val album = albums().first()

        assertEquals(album.right(), repository.getAlbum(album.id))
    }

    @Test
    fun getAlbum_unknown_isNotFound() = runTest {
        val unknown = AlbumId(UNKNOWN_ID)

        assertEquals(LibraryError.AlbumNotFound(unknown).left(), repository.getAlbum(unknown))
    }

    @Test
    fun data_everyAlbumAndSongPointsToAnExistingParent() = runTest {
        val artistIds = artists().map { it.id }.toSet()
        val albums = albums()
        val albumsById = albums.associateBy { it.id }

        assertTrue(albums.all { it.artistId in artistIds })
        assertTrue(
            songs().all { song ->
                albumsById[song.albumId]?.artistId == song.artistId
            },
        )
    }

    private suspend fun artists() = repository.observeArtists().first().getOrNull().orEmpty()

    private suspend fun albums() = repository.observeAlbums().first().getOrNull().orEmpty()

    private suspend fun songs() = repository.observeSongs().first().getOrNull().orEmpty()

    private companion object {
        const val UNKNOWN_ID = 9_999L
    }
}
