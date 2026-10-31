package com.eduardoflores.rolabox.device.library.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.LibraryStatus
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.transformWhile

/** The library as queries on the index. Only the scanner reads the files (ADR-024, rule 6). */
@Singleton
internal class IndexLibraryRepository @Inject constructor(
    private val index: LibraryIndexLocalDataSource,
    private val files: AudioFilesLocalDataSource,
    private val scanner: LibraryScanner,
) : LibraryRepository {
    private val permissionGranted = MutableStateFlow(false)

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

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist> =
        index.getArtist(id).flatMap { it?.right() ?: LibraryError.ArtistNotFound(id).left() }

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album> =
        index.getAlbum(id).flatMap { it?.right() ?: LibraryError.AlbumNotFound(id).left() }

    override fun observeStatus(): Flow<Either<LibraryError, LibraryStatus>> = combine(
        permissionGranted,
        scanner.phase,
        index.observeSongCount(),
    ) { granted, phase, count ->
        count.map { songs -> statusOf(granted, phase, songs) }
    }.distinctUntilChanged().endingAtFirstLeft()

    override fun checkPermission() {
        val granted = files.hasPermission()
        val wasGranted = permissionGranted.getAndUpdate { granted }
        if (granted && !wasGranted) scanner.start()
    }
}

private fun statusOf(granted: Boolean, phase: ScanPhase, songs: Int): LibraryStatus = when {
    !granted -> LibraryStatus.PermissionMissing
    songs > 0 -> LibraryStatus.Ready(readingTags = null)
    phase == ScanPhase.Finished -> LibraryStatus.Empty
    else -> LibraryStatus.Scanning
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
