package com.eduardoflores.rolabox.device.library.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.LibraryError

/** The music files on the phone, as the system lists them. The files themselves are never written (ADR-024). */
interface AudioFilesLocalDataSource {
    /** Whether the audio permission of this version of Android is granted. */
    suspend fun getPermissionGranted(): Boolean

    /** Every file marked as music on every storage volume. Ringtones, alarms and the like are left out. */
    suspend fun getAudioFiles(): Either<LibraryError, AudioFileListing>
}
