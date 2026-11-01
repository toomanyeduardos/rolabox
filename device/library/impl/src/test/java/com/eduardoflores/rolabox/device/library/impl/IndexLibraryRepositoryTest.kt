package com.eduardoflores.rolabox.device.library.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryStatus
import com.eduardoflores.rolabox.device.library.testing.FakeAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndexLibraryRepositoryTest {
    private val files = FakeAudioFilesLocalDataSource()
    private val index = FakeLibraryIndexLocalDataSource()

    private fun TestScope.repository(): IndexLibraryRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return IndexLibraryRepository(
            index,
            files,
            LibraryScanner(files, index, backgroundScope, dispatcher),
            backgroundScope,
            dispatcher,
        )
    }

    @Test
    fun beforeTheFirstCheckEnds_theStatusEmitsNothing() = runTest {
        val repository = repository()
        var emitted = 0
        backgroundScope.launch { repository.observeStatus().collect { emitted++ } }

        runCurrent()
        assertEquals(0, emitted)

        repository.checkPermission()
        runCurrent()
        assertEquals(LibraryStatus.Empty.right(), repository.observeStatus().first())
        assertEquals(true, emitted > 0)
    }

    @Test
    fun thePermissionAsked_isNotSetUntilTheDeviceSaysSo() = runTest {
        val repository = repository()
        assertEquals(false.right(), repository.observePermissionAsked().first())

        repository.setPermissionAsked()

        assertEquals(true.right(), repository.observePermissionAsked().first())
    }

    @Test
    fun checkingThePermission_returnsBeforeTheCheckHasRun() = runTest {
        val repository = repository()
        var emitted = 0
        backgroundScope.launch { repository.observeStatus().collect { emitted++ } }
        runCurrent()

        repository.checkPermission()

        assertEquals(0, emitted)
        assertEquals(0, files.listCount)
    }

    @Test
    fun withoutThePermission_thePermissionIsMissing_andNothingScans() = runTest {
        files.permissionGranted = false
        val repository = repository()

        repository.checkPermission()
        runCurrent()

        assertEquals(LibraryStatus.PermissionMissing.right(), repository.observeStatus().first())
        assertEquals(0, files.listCount)
    }

    @Test
    fun whileTheFirstScanRuns_theStatusIsScanning() = runTest {
        files.holdList = CompletableDeferred()
        val repository = repository()

        repository.checkPermission()
        runCurrent()

        assertEquals(LibraryStatus.Scanning.right(), repository.observeStatus().first())
    }

    @Test
    fun aScanThatFindsNothing_isEmpty() = runTest {
        val repository = repository()

        repository.checkPermission()
        runCurrent()

        assertEquals(LibraryStatus.Empty.right(), repository.observeStatus().first())
    }

    @Test
    fun aScanThatFindsSongs_isReady() = runTest {
        files.files = listOf(audioFile(1))
        val repository = repository()

        repository.checkPermission()
        runCurrent()

        assertEquals(LibraryStatus.Ready(readingTags = null).right(), repository.observeStatus().first())
    }

    @Test
    fun thePermissionFoundGranted_startsAScan_onlyTheFirstTime() = runTest {
        files.permissionGranted = false
        val repository = repository()
        repository.checkPermission()
        runCurrent()
        files.permissionGranted = true

        repository.checkPermission()
        runCurrent()
        repository.checkPermission()
        runCurrent()

        assertEquals(1, files.listCount)
    }

    @Test
    fun thePermissionGoingAway_keepsTheIndex() = runTest {
        files.files = listOf(audioFile(1))
        val repository = repository()
        repository.checkPermission()
        runCurrent()

        files.permissionGranted = false
        repository.checkPermission()
        runCurrent()

        assertEquals(LibraryStatus.PermissionMissing.right(), repository.observeStatus().first())
        assertEquals(1, index.songs.size)
    }

    @Test
    fun aFailedScanOfAnEmptyIndex_emitsTheError_andEndsTheFlow() = runTest {
        val error = LibraryError.Storage(StorageError.Unavailable)
        files.listError = error
        val repository = repository()

        repository.checkPermission()
        runCurrent()

        assertEquals(listOf(error.left()), repository.observeStatus().toList())
    }

    @Test
    fun aFailedScanWithSongsInTheIndex_isStillReady() = runTest {
        files.files = listOf(audioFile(1))
        val repository = repository()
        repository.checkPermission()
        runCurrent()

        files.permissionGranted = false
        repository.checkPermission()
        runCurrent()
        files.permissionGranted = true
        files.listError = LibraryError.Storage(StorageError.Unavailable)
        repository.checkPermission()
        runCurrent()

        assertEquals(LibraryStatus.Ready(readingTags = null).right(), repository.observeStatus().first())
    }

    @Test
    fun theSongOfTheIndex_hasItsUri() = runTest {
        files.files = listOf(audioFile(1))
        val repository = repository()
        repository.checkPermission()
        runCurrent()

        val song = repository.observeSongs().first().getOrNull().orEmpty().single()

        assertEquals("content://media/external/audio/media/1", song.uri)
    }

    @Test
    fun anArtistOnACompilation_opensThatAlbum_andTheAlbumArtistOpensItsSongs() = runTest {
        files.files = listOf(
            audioFile(1, artist = "A", albumArtist = "Various", album = "Hits"),
            audioFile(2, artist = "B", albumArtist = "Various", album = "Hits"),
        )
        val repository = repository()
        repository.checkPermission()
        runCurrent()
        val artist = index.artists.first { it.name == "A" }.id
        val various = index.artists.first { it.name == "Various" }.id

        assertEquals(
            listOf("Hits"),
            repository.observeAlbumsByArtist(artist).first().getOrNull().orEmpty().map { it.title },
        )
        assertEquals(2, repository.observeSongsByArtist(various).first().getOrNull().orEmpty().size)
    }

    @Test
    fun anArtistThatIsNotThere_isNotFound_andEndsTheFlow() = runTest {
        val repository = repository()
        val missing = ArtistId(99)

        assertEquals(
            listOf(LibraryError.ArtistNotFound(missing).left()),
            repository.observeAlbumsByArtist(missing).toList(),
        )
        assertEquals(LibraryError.ArtistNotFound(missing).left(), repository.getArtist(missing))
    }

    @Test
    fun anAlbumThatIsNotThere_isNotFound_andEndsTheFlow() = runTest {
        val repository = repository()
        val missing = AlbumId(99)

        assertEquals(
            listOf(LibraryError.AlbumNotFound(missing).left()),
            repository.observeSongsByAlbum(missing).toList(),
        )
        assertEquals(LibraryError.AlbumNotFound(missing).left(), repository.getAlbum(missing))
    }
}
