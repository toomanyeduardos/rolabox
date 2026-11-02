package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.AudioFileChanges
import com.eduardoflores.rolabox.device.library.local.api.AudioFileListing
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Files that tests set. With [holdList] set, a listing waits for it, so a test can act during a scan. A generation
 * says what changed, and a fake has none, so a test says it in [changedFiles]. [files] is what is still there.
 */
class FakeAudioFilesLocalDataSource : AudioFilesLocalDataSource {
    var permissionGranted = true
    var files: List<AudioFile> = emptyList()
    var changedFiles: List<AudioFile> = emptyList()
    var volumes: List<VolumeScanState> = emptyList()
    var listError: LibraryError? = null
    var holdList: CompletableDeferred<Unit>? = null

    /** How many times every file was listed. */
    var listCount = 0
        private set

    /** How many times only the changed files were. */
    var changesCount = 0
        private set
    private val permissionAsked = MutableStateFlow(false)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Says that the system's audio collection changed. */
    fun emitChange() {
        changes.tryEmit(Unit)
    }

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

    override suspend fun getVolumes(): Either<LibraryError, List<VolumeScanState>> =
        listError?.left() ?: volumes.right()

    override suspend fun getAudioFileChanges(since: List<VolumeScanState>): Either<LibraryError, AudioFileChanges> {
        changesCount++
        holdList?.await()
        return listError?.left()
            ?: AudioFileChanges(changedFiles, files.mapTo(HashSet()) { it.file.mediaStoreId }).right()
    }

    override fun observeChanges(): Flow<Unit> = changes
}
