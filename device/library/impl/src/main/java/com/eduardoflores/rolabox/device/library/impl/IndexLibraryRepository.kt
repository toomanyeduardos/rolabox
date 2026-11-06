package com.eduardoflores.rolabox.device.library.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Genre
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.LibraryStatus
import com.eduardoflores.rolabox.device.library.api.Progress
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/** The library as queries on the index. Only the scanner reads the files (ADR-024, rule 6). */
@Suppress("TooManyFunctions") // One for each function of the repository.
@Singleton
internal class IndexLibraryRepository @Inject constructor(
    private val index: LibraryIndexLocalDataSource,
    private val files: AudioFilesLocalDataSource,
    private val scanner: LibraryScanner,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {
    /** Null until the first check ends, so the status never says the permission is missing before it knows. */
    private val permissionGranted = MutableStateFlow<Boolean?>(null)

    override fun observeArtists(): Flow<Either<LibraryError, List<Artist>>> = index.observeArtists()

    override fun observeAlbums(): Flow<Either<LibraryError, List<Album>>> = index.observeAlbums()

    override fun observeSongs(): Flow<Either<LibraryError, List<Song>>> = index.observeSongs()

    override fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>> =
        index.observeAlbumsByArtist(artistId).ifPresent(index.observeArtist(artistId)) {
            LibraryError.ArtistNotFound(artistId)
        }

    override fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>> =
        index.observeSongsByArtist(artistId).ifPresent(index.observeArtist(artistId)) {
            LibraryError.ArtistNotFound(artistId)
        }

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> =
        index.observeSongsByAlbum(albumId).ifPresent(index.observeAlbum(albumId)) {
            LibraryError.AlbumNotFound(albumId)
        }

    override fun observeGenres(): Flow<Either<LibraryError, List<Genre>>> = index.observeGenres()

    override fun observeArtistsByGenre(genreId: GenreId): Flow<Either<LibraryError, List<Artist>>> =
        index.observeArtistsByGenre(genreId).ifPresent(index.observeGenre(genreId)) {
            LibraryError.GenreNotFound(genreId)
        }

    override fun observeAlbumsByGenreAndArtist(
        genreId: GenreId,
        artistId: ArtistId,
    ): Flow<Either<LibraryError, List<Album>>> = index.observeAlbumsByGenreAndArtist(genreId, artistId)
        .ifPresent(index.observeGenre(genreId)) { LibraryError.GenreNotFound(genreId) }
        .ifPresent(index.observeArtist(artistId)) { LibraryError.ArtistNotFound(artistId) }

    override fun observeSongsByGenreAndArtist(
        genreId: GenreId,
        artistId: ArtistId,
    ): Flow<Either<LibraryError, List<Song>>> = index.observeSongsByGenreAndArtist(genreId, artistId)
        .ifPresent(index.observeGenre(genreId)) { LibraryError.GenreNotFound(genreId) }
        .ifPresent(index.observeArtist(artistId)) { LibraryError.ArtistNotFound(artistId) }

    override fun observeSongsByGenreAndAlbum(
        genreId: GenreId,
        albumId: AlbumId,
    ): Flow<Either<LibraryError, List<Song>>> = index.observeSongsByGenreAndAlbum(genreId, albumId)
        .ifPresent(index.observeGenre(genreId)) { LibraryError.GenreNotFound(genreId) }
        .ifPresent(index.observeAlbum(albumId)) { LibraryError.AlbumNotFound(albumId) }

    override suspend fun getGenre(id: GenreId): Either<LibraryError, Genre> =
        index.getGenre(id).flatMap { it?.right() ?: LibraryError.GenreNotFound(id).left() }

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> =
        index.getArtist(id).flatMap { it?.right() ?: LibraryError.ArtistNotFound(id).left() }

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> =
        index.getAlbum(id).flatMap { it?.right() ?: LibraryError.AlbumNotFound(id).left() }

    override fun observeStatus(): Flow<Either<LibraryError, LibraryStatus>> = combine(
        permissionGranted.filterNotNull(),
        scanner.phase,
        scanner.readingTags,
        index.observeSongCount(),
    ) { granted, phase, readingTags, count ->
        count.flatMap { songs -> statusOf(granted, phase, readingTags, songs) }
    }.distinctUntilChanged().endingAtFirstLeft()

    override fun checkPermission() {
        scope.launch(ioDispatcher) {
            val granted = files.getPermissionGranted()
            val wasGranted = permissionGranted.getAndUpdate { granted }
            if (granted && wasGranted != true) scanner.keepCurrent()
        }
    }

    override fun observeSongLocations(): Flow<Either<LibraryError, Map<SongId, String>>> =
        index.observeSongLocations().distinctUntilChanged().endingAtFirstLeft()

    override fun observePermissionAsked(): Flow<Either<LibraryError, Boolean>> = files.observePermissionAsked()

    override suspend fun setPermissionAsked(): Either<LibraryError, Unit> = files.setPermissionAsked()
}

// A failed scan matters only while the index is empty: with songs, what it holds stays readable.
private fun statusOf(
    granted: Boolean,
    phase: ScanPhase,
    readingTags: Progress?,
    songs: Int,
): Either<LibraryError, LibraryStatus> = when {
    !granted -> LibraryStatus.PermissionMissing.right()
    songs > 0 -> LibraryStatus.Ready(readingTags).right()
    phase is ScanPhase.Failed -> phase.error.left()
    phase == ScanPhase.Finished -> LibraryStatus.Empty.right()
    else -> LibraryStatus.Scanning.right()
}

/** [this] list for as long as [owner] exists. When it doesn't, emits [notFound] and ends. */
private fun <T, O> Flow<Either<LibraryError, T>>.ifPresent(
    owner: Flow<Either<LibraryError, O?>>,
    notFound: () -> LibraryError,
): Flow<Either<LibraryError, T>> = combine(owner, this) { present, list ->
    present.flatMap { if (it == null) notFound().left() else list }
}.endingAtFirstLeft()

private fun <E, T> Flow<Either<E, T>>.endingAtFirstLeft(): Flow<Either<E, T>> = transformWhile {
    emit(it)
    it.isRight()
}
