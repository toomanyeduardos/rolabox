package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.launchIn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()

    private fun viewModel() =
        AboutViewModelImpl(library, appVersion = { "2.3" }).also { it.uiState.launchIn(it.viewModelScope) }

    @Test
    fun showsTheVersion_andTheNumberOfSongs() {
        library.songs = (1L..3L).map { song(it) }

        assertEquals(AboutUiState(version = "2.3", songs = SongCount.Loaded(3)), viewModel().uiState.value)
    }

    @Test
    fun theNumberOfSongs_followsTheLibrary() {
        val viewModel = viewModel()
        assertEquals(SongCount.Loaded(0), viewModel.uiState.value.songs)

        library.songs = listOf(song(1))

        assertEquals(SongCount.Loaded(1), viewModel.uiState.value.songs)
    }

    @Test
    fun aLibraryThatCannotBeRead_saysSo_andStillShowsTheVersion() {
        library.setReadError(StorageError.Corrupted)

        assertEquals(AboutUiState(version = "2.3", songs = SongCount.Unavailable), viewModel().uiState.value)
    }

    private fun song(id: Long) = Song(
        id = SongId(id),
        albumId = AlbumId(1),
        artistId = ArtistId(1),
        title = "Song $id",
        trackNumber = id.toInt(),
        duration = 3.minutes,
    )
}
