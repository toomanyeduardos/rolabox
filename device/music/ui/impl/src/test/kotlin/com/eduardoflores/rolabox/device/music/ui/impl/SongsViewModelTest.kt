package com.eduardoflores.rolabox.device.music.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SongsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = filledLibrary()

    private fun TestScope.observedViewModel(source: SongsSource) = SongsViewModelImpl(source, library).also { vm ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { vm.uiState.collect() }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { vm.title.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        val viewModel = SongsViewModelImpl(SongsSource.Album(PaperSatellites.id), library)

        assertEquals(ListUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun anAlbum_isItsSongsInTrackOrder() = runTest {
        val state = observedViewModel(SongsSource.Album(PaperSatellites.id)).uiState.value

        assertEquals(ListUiState.Loaded(listOf(Coastline, Glasshouse)), state)
    }

    @Test
    fun anArtist_isAllTheirSongsAcrossAlbums() = runTest {
        val state = observedViewModel(SongsSource.Artist(MarenHolt.id)).uiState.value

        assertEquals(ListUiState.Loaded(listOf(Coastline, DialTone, Glasshouse)), state)
    }

    @Test
    fun anAlbumWithoutSongs_isLoadedAndEmpty() = runTest {
        library.songs = emptyList()

        val state = observedViewModel(SongsSource.Album(PaperSatellites.id)).uiState.value

        assertEquals(ListUiState.Loaded(emptyList<Nothing>()), state)
    }

    @Test
    fun unknownAlbum_isFailed() = runTest {
        assertEquals(ListUiState.Failed, observedViewModel(SongsSource.Album(AlbumId(UNKNOWN_ID))).uiState.value)
    }

    @Test
    fun unknownArtist_isFailed() = runTest {
        assertEquals(ListUiState.Failed, observedViewModel(SongsSource.Artist(ArtistId(UNKNOWN_ID))).uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, observedViewModel(SongsSource.Album(PaperSatellites.id)).uiState.value)
    }

    @Test
    fun title_ofAnAlbum_isTheAlbumsName() = runTest {
        assertEquals(
            ListTitle.Album("Paper Satellites"),
            observedViewModel(SongsSource.Album(PaperSatellites.id)).title.value,
        )
    }

    @Test
    fun title_ofAllSongsOfAnArtist_isTheArtistsName() = runTest {
        assertEquals(ListTitle.Artist("Maren Holt"), observedViewModel(SongsSource.Artist(MarenHolt.id)).title.value)
    }

    @Test
    fun title_ofAnUnknownAlbum_isNone() = runTest {
        assertNull(observedViewModel(SongsSource.Album(AlbumId(UNKNOWN_ID))).title.value)
    }

    @Test
    fun title_whenTheLibraryCantBeRead_isNone() = runTest {
        library.setReadError(StorageError.Corrupted)

        assertNull(observedViewModel(SongsSource.Album(PaperSatellites.id)).title.value)
    }

    private companion object {
        const val UNKNOWN_ID = 999L
    }
}
