package com.eduardoflores.rolabox.device.library.local.impl.artwork

import android.content.Context
import android.graphics.BitmapFactory
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource.Companion.ARTWORK_SIZE
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.local.impl.index.AlbumArtworkDao
import com.eduardoflores.rolabox.device.library.local.impl.index.AlbumArtworkEntity
import com.eduardoflores.rolabox.device.library.local.impl.index.ArtworkAlbumRow
import com.eduardoflores.rolabox.device.library.local.impl.index.ArtworkState
import com.eduardoflores.rolabox.device.library.local.impl.index.SONG_ARTIST_SEPARATOR
import com.eduardoflores.rolabox.device.library.local.impl.index.toLibraryError
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.toArtworkImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The folder of the downloaded covers, in the app's files, which the system doesn't clear as it can the cache
 * (ADR-027).
 */
internal const val ARTWORK_FOLDER = "artwork"

private const val DOWNLOAD_ENABLED_KEY = "library.artwork.download_enabled"

/**
 * The records in the library's database, the images in [ARTWORK_FOLDER], and the setting in [PreferencesStore], on
 * this phone and unsynced, as the library's flag for the permission request is (ADR-027).
 */
@Suppress("TooManyFunctions") // One function for each of the store's.
internal class RoomAlbumArtworkLocalDataSource(
    private val dao: AlbumArtworkDao,
    private val folder: File,
    private val preferences: PreferencesStore,
    private val ioDispatcher: CoroutineDispatcher,
) : AlbumArtworkLocalDataSource {
    @Inject
    constructor(
        dao: AlbumArtworkDao,
        @ApplicationContext context: Context,
        preferences: PreferencesStore,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(dao, File(context.filesDir, ARTWORK_FOLDER), preferences, ioDispatcher)

    override suspend fun getAlbums(): Either<LibraryError, List<ArtworkAlbum>> = catchNamed(::toLibraryError) {
        withContext(ioDispatcher) { dao.albums().mapNotNull(ArtworkAlbumRow::toArtworkAlbum) }
    }

    override suspend fun getAlbum(key: String): Either<LibraryError, ArtworkAlbum?> =
        catchNamed(::toLibraryError) { dao.album(key)?.toArtworkAlbum() }

    override suspend fun getAlbumOfSong(song: SongId): Either<LibraryError, ArtworkAlbum?> =
        catchNamed(::toLibraryError) { dao.albumOfSong(song.value)?.toArtworkAlbum() }

    override fun observeRecord(key: String): Flow<Either<LibraryError, ArtworkRecord?>> =
        dao.observe(key).map { it?.toRecord() }.flowOn(ioDispatcher).catchNamed(::toLibraryError)

    override suspend fun setRecord(key: String, record: ArtworkRecord): Either<LibraryError, Unit> =
        catchNamed(::toLibraryError) { dao.upsert(record.toEntity(key)) }

    override suspend fun saveCover(
        key: String,
        sourceId: String,
        bytes: ByteArray,
        foundAt: Long,
    ): Either<LibraryError, Unit> = either {
        val fileName = "${UUID.randomUUID()}.jpg"
        writeFile(fileName, bytes).bind()
        setRecord(key, ArtworkRecord.Found(fileName, sourceId, foundAt)).bind()
    }

    override suspend fun loadCover(fileName: String): Either<LibraryError, ArtworkImage?> = withContext(ioDispatcher) {
        val file = File(folder, fileName)
        if (!file.isFile) return@withContext Either.Right(null)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        // A file that is gone, or isn't an image, decodes to nothing.
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext Either.Right(null)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
        }
        Either.Right(BitmapFactory.decodeFile(file.path, options)?.toArtworkImage())
    }

    override suspend fun deleteCover(fileName: String): Either<LibraryError, Unit> = withContext(ioDispatcher) {
        catchNamed(::toFileError) {
            val file = File(folder, fileName)
            if (file.exists() && !file.delete()) throw IOException("Couldn't delete $file")
        }
    }

    override fun observeDownloadEnabled(): Flow<Either<LibraryError, Boolean>> =
        preferences.observeString(DOWNLOAD_ENABLED_KEY).map { stored ->
            // On until the user turns it off (ADR-027).
            stored.mapLeft(LibraryError::Storage).map { it != false.toString() }
        }

    override suspend fun setDownloadEnabled(enabled: Boolean): Either<LibraryError, Unit> =
        preferences.setString(DOWNLOAD_ENABLED_KEY, enabled.toString()).mapLeft(LibraryError::Storage)

    // Written next to where it goes and renamed, so a file that is there is whole.
    private suspend fun writeFile(fileName: String, bytes: ByteArray): Either<LibraryError, Unit> =
        withContext(ioDispatcher) {
            catchNamed(::toFileError) {
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Couldn't make $folder")
                val partial = File(folder, "$fileName.partial")
                partial.writeBytes(bytes)
                if (!partial.renameTo(File(folder, fileName))) {
                    partial.delete()
                    throw IOException("Couldn't keep $fileName")
                }
            }
        }
}

/** The power of two that brings [longestSide] down to at most [ARTWORK_SIZE], as `BitmapFactory` samples. */
internal fun sampleSizeFor(longestSide: Int): Int {
    var sampleSize = 1
    while (longestSide / (sampleSize * 2) >= ARTWORK_SIZE) sampleSize *= 2
    return sampleSize
}

private fun toFileError(error: Throwable): LibraryError? = when (error) {
    is IOException -> LibraryError.Storage(StorageError.Unavailable)
    else -> null
}

private fun ArtworkAlbumRow.toArtworkAlbum(): ArtworkAlbum? = artworkSongUri?.let { uri ->
    ArtworkAlbum(
        key = albumKey,
        title = title,
        artistName = artistName,
        isVarious = isVarious,
        artworkSongUri = uri,
        record = state?.let { recordState ->
            AlbumArtworkEntity(
                albumKey,
                recordState,
                fileName,
                sourceId,
                foundAt,
                attempts ?: 0,
                lastAttemptAt,
            ).toRecord()
        },
        songArtists = songArtists?.split(SONG_ARTIST_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
        highestTrackNumber = highestTrackNumber,
        year = year,
    )
}

private fun AlbumArtworkEntity.toRecord(): ArtworkRecord? = when (state) {
    ArtworkState.Pending -> ArtworkRecord.Pending

    // A row that says found without its file is one that was never whole: it is queued again.
    ArtworkState.Found -> if (fileName != null) {
        ArtworkRecord.Found(fileName, sourceId.orEmpty(), foundAt ?: 0)
    } else {
        ArtworkRecord.Pending
    }

    ArtworkState.NotFound -> ArtworkRecord.NotFound(attempts, lastAttemptAt ?: 0)

    ArtworkState.OnPhone -> ArtworkRecord.OnPhone
}

private fun ArtworkRecord.toEntity(key: String): AlbumArtworkEntity = when (this) {
    ArtworkRecord.Pending -> AlbumArtworkEntity(key, ArtworkState.Pending)

    is ArtworkRecord.Found -> AlbumArtworkEntity(key, ArtworkState.Found, fileName, sourceId, foundAt)

    is ArtworkRecord.NotFound -> AlbumArtworkEntity(
        key,
        ArtworkState.NotFound,
        attempts = attempts,
        lastAttemptAt = lastAttemptAt,
    )

    ArtworkRecord.OnPhone -> AlbumArtworkEntity(key, ArtworkState.OnPhone)
}
