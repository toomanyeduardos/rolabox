package com.eduardoflores.rolabox.device.library.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Serves [SampleLibrary]. TEMPORARY: there is no local database, which ADR-002 expects, so this is
 * replaced when the real library is designed. The data never changes, so every flow emits once and
 * completes. Nothing here blocks, so nothing switches dispatcher.
 */
internal class InMemoryLibraryRepository @Inject constructor() : LibraryRepository {
    private val library = SampleLibrary.Default
    private val artists = library.artists.sortedBy { it.name }
    private val albums = library.albums.sortedWith(compareBy({ it.year }, { it.title }))
    private val songs = library.songs.sortedBy { it.title }

    override fun observeArtists(): Flow<Either<LibraryError, List<Artist>>> = flowOf(artists.right())

    override fun observeAlbums(): Flow<Either<LibraryError, List<Album>>> = flowOf(albums.right())

    override fun observeSongs(): Flow<Either<LibraryError, List<Song>>> = flowOf(songs.right())

    override fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>> = flowOf(
        if (artists.any { it.id == artistId }) {
            albums.filter { it.artistId == artistId }.right()
        } else {
            LibraryError.ArtistNotFound(artistId).left()
        },
    )

    override fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>> = flowOf(
        if (artists.any { it.id == artistId }) {
            songs.filter { it.artistId == artistId }.right()
        } else {
            LibraryError.ArtistNotFound(artistId).left()
        },
    )

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> = flowOf(
        if (albums.any { it.id == albumId }) {
            library.songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }.right()
        } else {
            LibraryError.AlbumNotFound(albumId).left()
        },
    )

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> =
        artists.firstOrNull { it.id == id }?.right() ?: LibraryError.ArtistNotFound(id).left()

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> =
        albums.firstOrNull { it.id == id }?.right() ?: LibraryError.AlbumNotFound(id).left()
}
