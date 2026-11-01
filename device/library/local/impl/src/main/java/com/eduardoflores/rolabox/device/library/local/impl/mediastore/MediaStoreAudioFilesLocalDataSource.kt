package com.eduardoflores.rolabox.device.library.local.impl.mediastore

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.AudioColumns
import androidx.annotation.VisibleForTesting
import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.AudioFileListing
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** MediaStore's audio collection, on every volume at once. Only this module knows its columns (ADR-024, rule 2). */
internal class MediaStoreAudioFilesLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: PreferencesStore,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AudioFilesLocalDataSource {
    override suspend fun getPermissionGranted(): Boolean = withContext(ioDispatcher) {
        context.checkSelfPermission(audioPermission(Build.VERSION.SDK_INT)) == PackageManager.PERMISSION_GRANTED
    }

    override fun observePermissionAsked(): Flow<Either<LibraryError, Boolean>> =
        preferences.observeString(PERMISSION_ASKED_KEY).map { stored ->
            stored.mapLeft(LibraryError::Storage).map { it != null }
        }

    override suspend fun setPermissionAsked(): Either<LibraryError, Unit> =
        preferences.setString(PERMISSION_ASKED_KEY, "true").mapLeft(LibraryError::Storage)

    override suspend fun getAudioFiles(): Either<LibraryError, AudioFileListing> = catchNamed(::toLibraryError) {
        withContext(ioDispatcher) {
            // The volumes first: a change during the query is then seen as a change by the next comparison.
            val volumes = volumeStates()
            AudioFileListing(queryMusic(context.contentResolver, Build.VERSION.SDK_INT), volumes)
        }
    }

    private fun volumeStates(): List<VolumeScanState> = MediaStore.getExternalVolumeNames(context).map { volume ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VolumeScanState(volume, MediaStore.getVersion(context, volume), MediaStore.getGeneration(context, volume))
        } else {
            VolumeScanState(volume, MediaStore.getVersion(context), generation = null)
        }
    }
}

private const val PERMISSION_ASKED_KEY = "library_audio_permission_asked"

internal fun audioPermission(sdk: Int): String = if (sdk >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

/** The items marked as music, without the kinds of audio that aren't, whatever else they are marked as. */
@VisibleForTesting
internal fun musicSelection(sdk: Int): String = buildList {
    add("${AudioColumns.IS_MUSIC} != 0")
    add("${AudioColumns.IS_RINGTONE} = 0")
    add("${AudioColumns.IS_NOTIFICATION} = 0")
    add("${AudioColumns.IS_ALARM} = 0")
    if (sdk >= Build.VERSION_CODES.S) add("${AudioColumns.IS_RECORDING} = 0")
}.joinToString(" AND ")

// The columns that exist on every API this app runs on, then those added later.
private val BaseColumns = listOf(
    AudioColumns._ID,
    AudioColumns.VOLUME_NAME,
    AudioColumns.RELATIVE_PATH,
    AudioColumns.DISPLAY_NAME,
    AudioColumns.SIZE,
    AudioColumns.DATE_MODIFIED,
    AudioColumns.TITLE,
    AudioColumns.ARTIST,
    AudioColumns.ALBUM,
    AudioColumns.TRACK,
    AudioColumns.YEAR,
    AudioColumns.DURATION,
)

private fun projection(sdk: Int): List<String> = if (sdk >= Build.VERSION_CODES.R) {
    BaseColumns + listOf(AudioColumns.ALBUM_ARTIST, AudioColumns.DISC_NUMBER, AudioColumns.COMPILATION)
} else {
    BaseColumns
}

/** One query for every volume: [MediaStore.VOLUME_EXTERNAL] is all of them, an SD card included. */
@VisibleForTesting
internal fun queryMusic(resolver: ContentResolver, sdk: Int): List<AudioFile> {
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val cursor = resolver.query(collection, projection(sdk).toTypedArray(), musicSelection(sdk), null, null)
        ?: throw IOException("MediaStore answered with no cursor")
    return cursor.use { rows ->
        buildList(rows.count) {
            while (rows.moveToNext()) add(rows.toAudioFile())
        }
    }
}

private fun Cursor.toAudioFile(): AudioFile {
    val id = getLong(getColumnIndexOrThrow(AudioColumns._ID))
    val volume = text(AudioColumns.VOLUME_NAME) ?: MediaStore.VOLUME_EXTERNAL
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volume), id)
    return MediaStoreRow(
        id = id,
        relativePath = text(AudioColumns.RELATIVE_PATH),
        displayName = text(AudioColumns.DISPLAY_NAME),
        size = long(AudioColumns.SIZE),
        dateModified = long(AudioColumns.DATE_MODIFIED),
        title = text(AudioColumns.TITLE),
        artist = text(AudioColumns.ARTIST),
        albumArtist = text(AudioColumns.ALBUM_ARTIST),
        album = text(AudioColumns.ALBUM),
        track = long(AudioColumns.TRACK)?.toInt(),
        discNumber = text(AudioColumns.DISC_NUMBER),
        year = long(AudioColumns.YEAR)?.toInt(),
        compilation = text(AudioColumns.COMPILATION),
        durationMillis = long(AudioColumns.DURATION),
    ).toAudioFile(uri.toString())
}

// A column this API doesn't have reads as null, like a value the file doesn't have.
private fun Cursor.text(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

private fun Cursor.long(column: String): Long? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

private fun toLibraryError(error: Throwable): LibraryError? = when (error) {
    // The permission went away, MediaStore's database failed, or it didn't answer.
    is SecurityException, is SQLiteException, is IOException -> LibraryError.Storage(StorageError.Unavailable)

    else -> null
}
