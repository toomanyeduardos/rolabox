package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.AudioFileListing
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import kotlinx.coroutines.CompletableDeferred

/** Files that tests set. With [holdList] set, a listing waits for it, so a test can act during a scan. */
class FakeAudioFilesLocalDataSource : AudioFilesLocalDataSource {
    var permissionGranted = true
    var files: List<AudioFile> = emptyList()
    var volumes: List<VolumeScanState> = emptyList()
    var listError: LibraryError? = null
    var holdList: CompletableDeferred<Unit>? = null
    var listCount = 0
        private set

    override fun hasPermission(): Boolean = permissionGranted

    override suspend fun list(): Either<LibraryError, AudioFileListing> {
        listCount++
        holdList?.await()
        return listError?.left() ?: AudioFileListing(files, volumes).right()
    }
}
