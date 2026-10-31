package com.eduardoflores.rolabox.device.library.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * The music library: artists, their albums and the songs on them.
 *
 * Artists are sorted by name, albums by year and then title, and songs by title, except the songs of one
 * album, which follow the disc and track order. A missing value sorts last.
 *
 * Any read can fail, because the library may be kept anywhere. A `Left` ends the flow.
 */
interface LibraryRepository {
    fun observeArtists(): Flow<Either<LibraryError, List<Artist>>>

    fun observeAlbums(): Flow<Either<LibraryError, List<Album>>>

    fun observeSongs(): Flow<Either<LibraryError, List<Song>>>

    /**
     * The albums of the artist, and the albums of others they have a song on. Emits
     * [LibraryError.ArtistNotFound] and ends when there is no such artist.
     */
    fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>>

    /**
     * Every song of the artist and of their albums, sorted by title. Emits
     * [LibraryError.ArtistNotFound] and ends when there is no such artist.
     */
    fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>>

    /** Emits [LibraryError.AlbumNotFound] and ends when there is no such album. */
    fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>>

    suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist>

    suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album>

    /** Emits the error and ends when a scan failed and the index is empty, since there is then nothing to show. */
    fun observeStatus(): Flow<Either<LibraryError, LibraryStatus>>

    /**
     * Checks the audio permission again, and starts a scan if it is now granted and wasn't before. The first call
     * of a process is the scan of that process's start. It returns without waiting: [observeStatus] emits once the
     * first check has ended.
     */
    fun checkPermission()
}
