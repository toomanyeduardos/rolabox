package com.eduardoflores.rolabox.device.library.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * The music library: artists, their albums and the songs on them.
 *
 * Artists are sorted by name, albums by year and then title, and songs by title, except the songs of one
 * album, which follow the track order.
 *
 * Any read can fail, because the library may be kept anywhere. A `Left` ends the flow.
 */
interface LibraryRepository {
    fun observeArtists(): Flow<Either<LibraryError, List<Artist>>>

    fun observeAlbums(): Flow<Either<LibraryError, List<Album>>>

    fun observeSongs(): Flow<Either<LibraryError, List<Song>>>

    /** Emits [LibraryError.ArtistNotFound] and ends when there is no such artist. */
    fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>>

    /**
     * Every song of the artist, across their albums and sorted by title. Emits
     * [LibraryError.ArtistNotFound] and ends when there is no such artist.
     */
    fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>>

    /** Emits [LibraryError.AlbumNotFound] and ends when there is no such album. */
    fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>>

    suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist>

    suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album>
}
