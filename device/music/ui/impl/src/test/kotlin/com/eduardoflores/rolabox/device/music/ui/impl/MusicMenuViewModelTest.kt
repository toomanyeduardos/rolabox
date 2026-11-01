package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.LibraryStatus
import com.eduardoflores.rolabox.device.library.api.Progress
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicMenuViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()

    private fun TestScope.observedViewModel() = MusicMenuViewModelImpl(library).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(MusicMenuUiState.Loading, MusicMenuViewModelImpl(library).uiState.value)
    }

    @Test
    fun permissionMissing_neverAsked_isNotAsked() = runTest {
        library.status = LibraryStatus.PermissionMissing
        val viewModel = observedViewModel()

        // Android's rationale check is false before any ask too, which must not read as permanently denied.
        viewModel.onResumed(canAskAgain = false)

        assertEquals(MusicMenuUiState.PermissionNotAsked, viewModel.uiState.value)
    }

    @Test
    fun whileThePromptShows_isDenied_andNotPermanentlyDenied() = runTest {
        library.status = LibraryStatus.PermissionMissing
        val viewModel = observedViewModel()
        viewModel.onResumed(canAskAgain = false)

        viewModel.onPermissionRequested()

        assertEquals(true, library.permissionAsked)
        assertEquals(MusicMenuUiState.PermissionDenied, viewModel.uiState.value)
    }

    @Test
    fun deniedOnce_isDenied() = runTest {
        library.status = LibraryStatus.PermissionMissing
        library.permissionAsked = true
        val viewModel = observedViewModel()

        viewModel.onPermissionResult(canAskAgain = true)

        assertEquals(MusicMenuUiState.PermissionDenied, viewModel.uiState.value)
    }

    @Test
    fun androidWontAskAgain_isPermanentlyDenied() = runTest {
        library.status = LibraryStatus.PermissionMissing
        library.permissionAsked = true
        val viewModel = observedViewModel()

        viewModel.onPermissionResult(canAskAgain = false)

        assertEquals(MusicMenuUiState.PermissionPermanentlyDenied, viewModel.uiState.value)
    }

    @Test
    fun comingBackAfterAnAskFromAnEarlierProcess_followsAndroid() = runTest {
        library.status = LibraryStatus.PermissionMissing
        library.permissionAsked = true
        val viewModel = observedViewModel()

        viewModel.onResumed(canAskAgain = false)

        assertEquals(MusicMenuUiState.PermissionPermanentlyDenied, viewModel.uiState.value)
    }

    @Test
    fun thePromptReturning_checksThePermission() = runTest {
        val viewModel = observedViewModel()

        viewModel.onPermissionResult(canAskAgain = true)

        assertEquals(1, library.permissionChecks)
    }

    @Test
    fun resuming_whileThePermissionIsMissing_checksIt() = runTest {
        library.status = LibraryStatus.PermissionMissing
        library.permissionAsked = true
        val viewModel = observedViewModel()

        viewModel.onResumed(canAskAgain = true)

        assertEquals(1, library.permissionChecks)
    }

    @Test
    fun resuming_withThePermission_doesntCheckIt() = runTest {
        val viewModel = observedViewModel()

        viewModel.onResumed(canAskAgain = true)

        assertEquals(0, library.permissionChecks)
    }

    @Test
    fun theFirstScan_isScanning() = runTest {
        library.status = LibraryStatus.Scanning

        assertEquals(MusicMenuUiState.Scanning, observedViewModel().uiState.value)
    }

    @Test
    fun noMusic_isEmpty() = runTest {
        library.status = LibraryStatus.Empty

        assertEquals(MusicMenuUiState.Empty, observedViewModel().uiState.value)
    }

    @Test
    fun filled_isTheMenu() = runTest {
        assertEquals(MusicMenuUiState.Menu(readingTags = null), observedViewModel().uiState.value)
    }

    @Test
    fun readingTags_isTheMenuWithItsProgress() = runTest {
        val progress = Progress(done = 40, total = 100)
        library.status = LibraryStatus.Ready(readingTags = progress)

        assertEquals(MusicMenuUiState.Menu(progress), observedViewModel().uiState.value)
    }

    @Test
    fun grantedLater_followsTheStatus() = runTest {
        library.status = LibraryStatus.PermissionMissing
        library.permissionAsked = true
        val viewModel = observedViewModel()

        library.status = LibraryStatus.Scanning

        assertEquals(MusicMenuUiState.Scanning, viewModel.uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertEquals(MusicMenuUiState.Failed, observedViewModel().uiState.value)
    }
}
