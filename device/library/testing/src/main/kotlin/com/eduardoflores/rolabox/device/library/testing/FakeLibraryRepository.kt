package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile

/**
 * A library that tests fill in: set [artists], [albums] and [songs], and the observed flows follow.
 * Like any repository, a `Left` ends its flow: a lookup of something that isn't there, or a read error
 * set with [setReadError].
 */
@Singleton
class FakeLibraryRepository @Inject constructor() : LibraryRepository {
    private val artistsState = MutableStateFlow<List<Artist>>(emptyList())
    private val albumsState = MutableStateFlow<List<Album>>(emptyList())
    private val songsState = MutableStateFlow<List<Song>>(emptyList())

    private val readError = MutableStateFlow<StorageError?>(null)

    var artists: List<Artist>
        get() = artistsState.value
        set(value) {
            artistsState.value = value
        }

    var albums: List<Album>
        get() = albumsState.value
        set(value) {
            albumsState.value = value
        }

    var songs: List<Song>
        get() = songsState.value
        set(value) {
            songsState.value = value
        }

    /** Makes every read fail with [error], and the observed flows emit it and end. */
    fun setReadError(error: StorageError) {
        readError.value = error
    }

    override fun observeArtists(): Flow<Either<LibraryError, List<Artist>>> = artistsState.read { it.right() }

    override fun observeAlbums(): Flow<Either<LibraryError, List<Album>>> = albumsState.read { it.right() }

    override fun observeSongs(): Flow<Either<LibraryError, List<Song>>> = songsState.read { it.right() }

    override fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>> =
        artistsState.read { artists ->
            if (artists.any { it.id == artistId }) {
                albums.filter { it.artistId == artistId }.right()
            } else {
                LibraryError.ArtistNotFound(artistId).left()
            }
        }

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> =
        albumsState.read { albums ->
            if (albums.any { it.id == albumId }) {
                songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }.right()
            } else {
                LibraryError.AlbumNotFound(albumId).left()
            }
        }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> = readError.value?.storageError()
        ?: (artists.firstOrNull { it.id == id }?.right() ?: LibraryError.ArtistNotFound(id).left())

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> = readError.value?.storageError()
        ?: (albums.firstOrNull { it.id == id }?.right() ?: LibraryError.AlbumNotFound(id).left())

    private fun <S, T> Flow<S>.read(transform: (S) -> Either<LibraryError, T>): Flow<Either<LibraryError, T>> =
        combine(this, readError) { state, error -> error?.storageError() ?: transform(state) }.endingAtFirstLeft()

    private fun StorageError.storageError() = LibraryError.Storage(this).left()
}

private fun <E, T> Flow<Either<E, T>>.endingAtFirstLeft(): Flow<Either<E, T>> = transformWhile {
    emit(it)
    it.isRight()
}
