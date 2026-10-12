package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ArtistAlbumsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = filledLibrary()

    private fun TestScope.observedViewModel(artistId: Long) =
        ArtistAlbumsViewModelImpl(artistId, library).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.title.collect() }
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

    @Test
    fun title_isTheArtistsName() = runTest {
        assertEquals("Maren Holt", observedViewModel(MarenHolt.id.value).title.value)
    }

    @Test
    fun title_ofAnUnknownArtist_isNone() = runTest {
        assertNull(observedViewModel(UNKNOWN_ID).title.value)
    }

    @Test
    fun title_whenTheLibraryCantBeRead_isNone() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertNull(observedViewModel(MarenHolt.id.value).title.value)
    }

    private companion object {
        const val UNKNOWN_ID = 999L
    }
}
