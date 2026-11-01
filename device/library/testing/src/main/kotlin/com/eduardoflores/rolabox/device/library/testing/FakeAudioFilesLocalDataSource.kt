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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Files that tests set. With [holdList] set, a listing waits for it, so a test can act during a scan. */
class FakeAudioFilesLocalDataSource : AudioFilesLocalDataSource {
    var permissionGranted = true
    var files: List<AudioFile> = emptyList()
    var volumes: List<VolumeScanState> = emptyList()
    var listError: LibraryError? = null
    var holdList: CompletableDeferred<Unit>? = null
    var listCount = 0
        private set
    private val permissionAsked = MutableStateFlow(false)

    override suspend fun getPermissionGranted(): Boolean = permissionGranted

    override fun observePermissionAsked(): Flow<Either<LibraryError, Boolean>> = permissionAsked.map { it.right() }

    override suspend fun setPermissionAsked(): Either<LibraryError, Unit> {
        permissionAsked.value = true
        return Unit.right()
    }

    override suspend fun getAudioFiles(): Either<LibraryError, AudioFileListing> {
        listCount++
        holdList?.await()
        return listError?.left() ?: AudioFileListing(files, volumes).right()
    }
}
