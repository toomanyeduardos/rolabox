package com.eduardoflores.rolabox.device.library.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlinx.coroutines.flow.Flow

/**
 * Rolabox's own index of the library, in the orders [com.eduardoflores.rolabox.device.library.api.LibraryRepository]
 * promises. A `Left` ends a flow. An artist or an album that isn't there is `null`, which only the repository
 * turns into an error.
 */
@Suppress("TooManyFunctions") // The lists the repository promises, and what a scan reads and writes.
interface LibraryIndexLocalDataSource {
    fun observeArtists(): Flow<Either<LibraryError, List<Artist>>>

    fun observeAlbums(): Flow<Either<LibraryError, List<Album>>>

    fun observeSongs(): Flow<Either<LibraryError, List<Song>>>

    fun observeArtist(id: ArtistId): Flow<Either<LibraryError, Artist?>>

    fun observeAlbum(id: AlbumId): Flow<Either<LibraryError, Album?>>

    fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>>

    fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>>

    fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>>

    fun observeSongCount(): Flow<Either<LibraryError, Int>>

    suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist?>

    suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album?>

    /** The file of a song, a duplicate included, or `null` when it isn't in the index. */
    suspend fun getSongFile(id: SongId): Either<LibraryError, SongFile?>

    /** Every song as indexed, for a scan to compare with what it found. */
    suspend fun getIndexedSongs(): Either<LibraryError, List<IndexedSong>>

    /** Writes [batch] in one transaction, so the lists change once and a killed process leaves a consistent index. */
    suspend fun write(batch: IndexBatch): Either<LibraryError, Unit>

    /** What the last scan saw of MediaStore, or nothing before the first one. */
    suspend fun getScanState(): Either<LibraryError, List<VolumeScanState>>

    /** The URI of every song, a duplicate included, since it is still a file that can play. */
    fun observeSongLocations(): Flow<Either<LibraryError, Map<SongId, String>>>

    /** Replaces what the last scan saw of MediaStore. */
    suspend fun saveScanState(volumes: List<VolumeScanState>): Either<LibraryError, Unit>
}
