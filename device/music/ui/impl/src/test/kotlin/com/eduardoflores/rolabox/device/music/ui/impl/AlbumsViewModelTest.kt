package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlbumsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()

    private fun TestScope.observedViewModel() = AlbumsViewModelImpl(library).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, AlbumsViewModelImpl(library).uiState.value)
    }

    @Test
    fun everyAlbum_isLoaded_inTheLibrarysOrder_acrossArtists() = runTest {
        library.albums = listOf(Harborlight, LowTideRadio, PaperSatellites)

        assertEquals(
            ListUiState.Loaded(listOf(Harborlight, LowTideRadio, PaperSatellites)),
            observedViewModel().uiState.value,
        )
    }

    @Test
    fun noAlbums_isLoadedAndEmpty() = runTest {
        assertEquals(ListUiState.Loaded(emptyList<Nothing>()), observedViewModel().uiState.value)
    }

    @Test
    fun albumsAdded_areFollowed() = runTest {
        val viewModel = observedViewModel()

        library.albums = listOf(Harborlight)

        assertEquals(ListUiState.Loaded(listOf(Harborlight)), viewModel.uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, observedViewModel().uiState.value)
    }
}
