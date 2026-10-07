package com.eduardoflores.rolabox.device.music.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtistAlbumsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = filledLibrary()

    private fun TestScope.observedViewModel(artistId: Long) =
        ArtistAlbumsViewModelImpl(artistId, library).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
        }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, ArtistAlbumsViewModelImpl(MarenHolt.id.value, library).uiState.value)
    }

    @Test
    fun albums_areThoseOfThatArtistOnly() = runTest {
        val state = observedViewModel(MarenHolt.id.value).uiState.value

        assertEquals(ListUiState.Loaded(listOf(LowTideRadio, PaperSatellites)), state)
    }

    @Test
    fun anArtistWithoutAlbums_isLoadedAndEmpty() = runTest {
        library.albums = emptyList()

        assertEquals(ListUiState.Loaded(emptyList<Nothing>()), observedViewModel(MarenHolt.id.value).uiState.value)
    }

    @Test
    fun unknownArtist_isFailed() = runTest {
        assertEquals(ListUiState.Failed, observedViewModel(UNKNOWN_ID).uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, observedViewModel(MarenHolt.id.value).uiState.value)
    }

    private companion object {
        const val UNKNOWN_ID = 999L
    }
}
