package com.eduardoflores.rolabox.device.library.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.LibraryError
import kotlinx.coroutines.flow.Flow

/** The music files on the phone, as the system lists them. The files themselves are never written (ADR-024). */
interface AudioFilesLocalDataSource {
    /** Whether the audio permission of this version of Android is granted. */
    suspend fun getPermissionGranted(): Boolean

    /** Whether the user has been asked for that permission on this install. A `Left` ends the flow. */
    fun observePermissionAsked(): Flow<Either<LibraryError, Boolean>>

    suspend fun setPermissionAsked(): Either<LibraryError, Unit>

    /** Every file marked as music on every storage volume. Ringtones, alarms and the like are left out. */
    suspend fun getAudioFiles(): Either<LibraryError, AudioFileListing>

    /** MediaStore's version and generation of each volume now. */
    suspend fun getVolumes(): Either<LibraryError, List<VolumeScanState>>

    /** The files changed since [since], which a generation can tell from API 30, and which files are still there. */
    suspend fun getAudioFileChanges(since: List<VolumeScanState>): Either<LibraryError, AudioFileChanges>

    /** Emits each time the system says its audio collection changed, for as long as it is collected. */
    fun observeChanges(): Flow<Unit>
}
