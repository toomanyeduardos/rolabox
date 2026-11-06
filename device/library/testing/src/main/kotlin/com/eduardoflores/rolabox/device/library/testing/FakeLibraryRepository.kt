package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Genre
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.LibraryStatus
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile

/**
 * A library that tests fill in: set [artists], [albums] and [songs], and the observed flows follow. [genres] are
 * listed as they are set, and [songGenres] says which genres each song is in.
 * Like any repository, a `Left` ends its flow: a lookup of something that isn't there, or a read error
 * set with [setReadError].
 */
@Suppress("TooManyFunctions") // One for each function of the repository.
@Singleton
class FakeLibraryRepository @Inject constructor() : LibraryRepository {
    private val artistsState = MutableStateFlow<List<Artist>>(emptyList())
    private val albumsState = MutableStateFlow<List<Album>>(emptyList())
    private val songsState = MutableStateFlow<List<Song>>(emptyList())
    private val genresState = MutableStateFlow<List<Genre>>(emptyList())
    private val songGenresState = MutableStateFlow<Map<SongId, Set<GenreId>>>(emptyMap())

    private val statusState = MutableStateFlow<LibraryStatus>(LibraryStatus.Ready(readingTags = null))

    private val permissionAskedState = MutableStateFlow(false)

    private val readError = MutableStateFlow<StorageError?>(null)

    /** How many times [checkPermission] was called. */
    var permissionChecks = 0
        private set

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

    var genres: List<Genre>
        get() = genresState.value
        set(value) {
            genresState.value = value
        }

    var songGenres: Map<SongId, Set<GenreId>>
        get() = songGenresState.value
        set(value) {
            songGenresState.value = value
        }

    var status: LibraryStatus
        get() = statusState.value
        set(value) {
            statusState.value = value
        }

    var permissionAsked: Boolean
        get() = permissionAskedState.value
        set(value) {
            permissionAskedState.value = value
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

    override fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>> =
        artistsState.read { artists ->
            if (artists.any { it.id == artistId }) {
                songs.filter { it.artistId == artistId }.sortedBy { it.title.orEmpty() }.right()
            } else {
                LibraryError.ArtistNotFound(artistId).left()
            }
        }

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> =
        albumsState.read { albums ->
            if (albums.any { it.id == albumId }) {
                songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber ?: Int.MAX_VALUE }.right()
            } else {
                LibraryError.AlbumNotFound(albumId).left()
            }
        }

    override fun observeGenres(): Flow<Either<LibraryError, List<Genre>>> = genresState.read { it.right() }

    override fun observeArtistsByGenre(genreId: GenreId): Flow<Either<LibraryError, List<Artist>>> =
        genreLists(genreId) {
            val inGenre = songsIn(genreId)
            val ids = inGenre.map { it.artistId } +
                albums.filter { album -> album.title != null && inGenre.any { it.albumId == album.id } }
                    .map { it.artistId }
            artists.filter { it.id in ids }.right()
        }

    override fun observeAlbumsByGenreAndArtist(
        genreId: GenreId,
        artistId: ArtistId,
    ): Flow<Either<LibraryError, List<Album>>> = genreLists(genreId) {
        withArtist(artistId) {
            val inGenre = songsIn(genreId)
            albums.filter { album ->
                album.title != null &&
                    inGenre.any { it.albumId == album.id && (it.artistId == artistId || album.artistId == artistId) }
            }
        }
    }

    override fun observeSongsByGenreAndArtist(
        genreId: GenreId,
        artistId: ArtistId,
    ): Flow<Either<LibraryError, List<Song>>> = genreLists(genreId) {
        withArtist(artistId) {
            val ownAlbums = albums.filter { it.artistId == artistId && it.title != null }.map { it.id }
            songsIn(genreId).filter { it.artistId == artistId || it.albumId in ownAlbums }
                .sortedBy { it.title.orEmpty() }
        }
    }

    override fun observeSongsByGenreAndAlbum(
        genreId: GenreId,
        albumId: AlbumId,
    ): Flow<Either<LibraryError, List<Song>>> = genreLists(genreId) {
        if (albums.any { it.id == albumId }) {
            songsIn(genreId).filter { it.albumId == albumId }.sortedBy { it.trackNumber ?: Int.MAX_VALUE }.right()
        } else {
            LibraryError.AlbumNotFound(albumId).left()
        }
    }

    // A list of a genre, which follows every part of the library it reads, and ends when the genre is gone.
    private fun <T> genreLists(
        genreId: GenreId,
        list: () -> Either<LibraryError, List<T>>,
    ): Flow<Either<LibraryError, List<T>>> =
        combine(genresState, songGenresState, artistsState, albumsState, songsState) { genres, _, _, _, _ -> genres }
            .read { genres ->
                if (genres.any { it.id == genreId }) list() else LibraryError.GenreNotFound(genreId).left()
            }

    private fun <T> withArtist(artistId: ArtistId, list: () -> List<T>): Either<LibraryError, List<T>> =
        if (artists.any { it.id == artistId }) list().right() else LibraryError.ArtistNotFound(artistId).left()

    private fun songsIn(genreId: GenreId): List<Song> = songs.filter { genreId in songGenres[it.id].orEmpty() }

    override fun observeSongLocations(): Flow<Either<LibraryError, Map<SongId, String>>> =
        songsState.read { songs -> songs.associate { it.id to it.uri }.right() }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> = readError.value?.storageError()
        ?: (artists.firstOrNull { it.id == id }?.right() ?: LibraryError.ArtistNotFound(id).left())

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> = readError.value?.storageError()
        ?: (albums.firstOrNull { it.id == id }?.right() ?: LibraryError.AlbumNotFound(id).left())

    override suspend fun getGenre(id: GenreId): Either<LibraryError, Genre> = readError.value?.storageError()
        ?: (genres.firstOrNull { it.id == id }?.right() ?: LibraryError.GenreNotFound(id).left())

    override fun observeStatus(): Flow<Either<LibraryError, LibraryStatus>> = statusState.read { it.right() }

    override fun checkPermission() {
        permissionChecks++
    }

    override fun observePermissionAsked(): Flow<Either<LibraryError, Boolean>> =
        permissionAskedState.read { it.right() }

    override suspend fun setPermissionAsked(): Either<LibraryError, Unit> {
        permissionAsked = true
        return Unit.right()
    }

    private fun <S, T> Flow<S>.read(transform: (S) -> Either<LibraryError, T>): Flow<Either<LibraryError, T>> =
        combine(this, readError) { state, error -> error?.storageError() ?: transform(state) }.endingAtFirstLeft()

    private fun StorageError.storageError() = LibraryError.Storage(this).left()
}

private fun <E, T> Flow<Either<E, T>>.endingAtFirstLeft(): Flow<Either<E, T>> = transformWhile {
    emit(it)
    it.isRight()
}
