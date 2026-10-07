package com.eduardoflores.rolabox.device.music.impl

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

class ArtistsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()

    private fun TestScope.observedViewModel() = ArtistsViewModelImpl(library).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, ArtistsViewModelImpl(library).uiState.value)
    }

    @Test
    fun artists_areLoaded() = runTest {
        library.artists = listOf(MarenHolt, BlueHarbor)

        assertEquals(ListUiState.Loaded(listOf(MarenHolt, BlueHarbor)), observedViewModel().uiState.value)
    }

    @Test
    fun noArtists_isLoadedAndEmpty() = runTest {
        assertEquals(ListUiState.Loaded(emptyList<Nothing>()), observedViewModel().uiState.value)
    }

    @Test
    fun artistsAdded_areFollowed() = runTest {
        val viewModel = observedViewModel()

        library.artists = listOf(MarenHolt)

        assertEquals(ListUiState.Loaded(listOf(MarenHolt)), viewModel.uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, observedViewModel().uiState.value)
    }
}
