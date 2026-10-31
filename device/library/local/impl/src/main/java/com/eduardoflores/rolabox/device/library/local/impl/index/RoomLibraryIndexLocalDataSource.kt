package com.eduardoflores.rolabox.device.library.local.impl.index

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The index in Room. A list is mapped off the main thread, since it can hold every song of the library. */
// One function for each function of the index.
@Suppress("TooManyFunctions")
internal class RoomLibraryIndexLocalDataSource @Inject constructor(
    private val dao: LibraryIndexDao,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : LibraryIndexLocalDataSource {
    override fun observeArtists(): Flow<Either<LibraryError, List<Artist>>> =
        dao.observeArtists().map { rows -> rows.map(ArtistEntity::toArtist) }.asRead()

    override fun observeAlbums(): Flow<Either<LibraryError, List<Album>>> =
        dao.observeAlbums().map { rows -> rows.map(AlbumEntity::toAlbum) }.asRead()

    override fun observeSongs(): Flow<Either<LibraryError, List<Song>>> =
        dao.observeSongs().map { rows -> rows.map(SongRow::toSong) }.asRead()

    override fun observeArtist(id: ArtistId): Flow<Either<LibraryError, Artist?>> =
        dao.observeArtist(id.value).map { it?.toArtist() }.asRead()

    override fun observeAlbum(id: AlbumId): Flow<Either<LibraryError, Album?>> =
        dao.observeAlbum(id.value).map { it?.toAlbum() }.asRead()

    override fun observeAlbumsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Album>>> =
        dao.observeAlbumsByArtist(artistId.value).map { rows -> rows.map(AlbumEntity::toAlbum) }.asRead()

    override fun observeSongsByArtist(artistId: ArtistId): Flow<Either<LibraryError, List<Song>>> =
        dao.observeSongsByArtist(artistId.value).map { rows -> rows.map(SongRow::toSong) }.asRead()

    override fun observeSongsByAlbum(albumId: AlbumId): Flow<Either<LibraryError, List<Song>>> =
        dao.observeSongsByAlbum(albumId.value).map { rows -> rows.map(SongRow::toSong) }.asRead()

    override fun observeSongCount(): Flow<Either<LibraryError, Int>> = dao.observeSongCount().asRead()

    override suspend fun getArtist(id: ArtistId): Either<LibraryError, Artist?> =
        catchNamed(::toLibraryError) { dao.getArtist(id.value)?.toArtist() }

    override suspend fun getAlbum(id: AlbumId): Either<LibraryError, Album?> =
        catchNamed(::toLibraryError) { dao.getAlbum(id.value)?.toAlbum() }

    override suspend fun indexedSongs(): Either<LibraryError, List<IndexedSong>> = catchNamed(::toLibraryError) {
        withContext(ioDispatcher) { dao.indexedSongs().map(IndexedSongRow::toIndexedSong) }
    }

    override suspend fun write(batch: IndexBatch): Either<LibraryError, Unit> =
        catchNamed(::toLibraryError) { dao.write(batch) }

    override suspend fun saveScanState(volumes: List<VolumeScanState>): Either<LibraryError, Unit> =
        catchNamed(::toLibraryError) {
            dao.replaceScanState(volumes.map { ScanStateEntity(it.volume, it.version, it.generation) })
        }

    private fun <T> Flow<T>.asRead(): Flow<Either<LibraryError, T>> = flowOn(ioDispatcher).catchNamed(::toLibraryError)
}

private fun toLibraryError(error: Throwable): LibraryError? = when (error) {
    is SQLiteDatabaseCorruptException -> LibraryError.Storage(StorageError.Corrupted)
    is SQLiteException -> LibraryError.Storage(StorageError.Unavailable)
    else -> null
}
