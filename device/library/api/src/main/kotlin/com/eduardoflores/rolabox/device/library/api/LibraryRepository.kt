package com.eduardoflores.rolabox.device.library.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * The music library: artists, their albums and the songs on them, and the genres of the songs.
 *
 * Artists are sorted by name and songs by title, both without a leading "The", except the songs of one album, which
 * follow the disc and track order. A missing value sorts last.
 *
 * Any read can fail, because the library may be kept anywhere. A `Left` ends the flow.
 */
@Suppress("TooManyFunctions") // The library's reads, and its one permission flow, stay in one place (ADR-024).
interface LibraryRepository {
    fun observeArtists(): Flow<Either<LibraryError, List<Artist>>>

    /**
     * Every album, the one of songs with no album included, sorted by title without a leading "The", for the list of
     * every album, which the fast-scroll letter follows.
     */
    fun observeAlbums(): Flow<Either<LibraryError, List<Album>>>

    fun observeSongs(): Flow<Either<LibraryError, List<Song>>>

    /**
     * The albums of the artist, and the albums of others they have a song on, but not the album of songs with no
     * album: those are in [observeSongsByArtist]. Sorted by year and then title, as an artist's albums came out. Emits
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

    /**
     * Every genre, sorted by name, with Unknown Genre, the one of songs with none, last. A song with several genres is
     * in each of them.
     */
    fun observeGenres(): Flow<Either<LibraryError, List<Genre>>>

    /**
     * The artists with a song in the genre: the songs' own artists, and the artists of those songs' albums, as the
     * list of every artist has both. Sorted as that list is. Emits [LibraryError.GenreNotFound] and ends when there
     * is no such genre.
     */
    fun observeArtistsByGenre(genreId: GenreId): Flow<Either<LibraryError, List<Artist>>>

    /**
     * The albums of [observeAlbumsByArtist] that have a song in the genre, sorted the same way. Emits
     * [LibraryError.GenreNotFound] or [LibraryError.ArtistNotFound] and ends when either is gone.
     */
    fun observeAlbumsByGenreAndArtist(genreId: GenreId, artistId: ArtistId): Flow<Either<LibraryError, List<Album>>>

    /**
     * The songs of [observeSongsByArtist] that are in the genre, sorted by title. Emits [LibraryError.GenreNotFound]
     * or [LibraryError.ArtistNotFound] and ends when either is gone.
     */
    fun observeSongsByGenreAndArtist(genreId: GenreId, artistId: ArtistId): Flow<Either<LibraryError, List<Song>>>

    /**
     * The songs of the album that are in the genre, in disc and track order. Emits [LibraryError.GenreNotFound] or
     * [LibraryError.AlbumNotFound] and ends when either is gone.
     */
    fun observeSongsByGenreAndAlbum(genreId: GenreId, albumId: AlbumId): Flow<Either<LibraryError, List<Song>>>

    /**
     * Where the audio of every song is, for playback to follow a song that is gone or has moved. It has the songs no
     * list shows too: a song that became a duplicate is still a file that plays.
     */
    fun observeSongLocations(): Flow<Either<LibraryError, Map<SongId, String>>>

    suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist>

    suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album>

    suspend fun getGenre(id: GenreId): Either<LibraryError, Genre>

    /** Emits the error and ends when a scan failed and the index is empty, since there is then nothing to show. */
    fun observeStatus(): Flow<Either<LibraryError, LibraryStatus>>

    /**
     * Checks the audio permission again, and starts a scan if it is now granted and wasn't before. The first call
     * of a process is the scan of that process's start. It returns without waiting: [observeStatus] emits once the
     * first check has ended.
     */
    fun checkPermission()

    /** Whether the device has asked the user for the audio permission on this install. A `Left` ends the flow. */
    fun observePermissionAsked(): Flow<Either<LibraryError, Boolean>>

    /** Records that the device has asked for the audio permission. It stays on this phone, and isn't synced. */
    suspend fun setPermissionAsked(): Either<LibraryError, Unit>
}
