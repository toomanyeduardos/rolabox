package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.Song
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The source is the one of the entry's key, so it is given when the entry creates the ViewModel. */
@HiltViewModel(assistedFactory = SongsViewModelImpl.Factory::class)
internal class SongsViewModelImpl @AssistedInject constructor(
    @Assisted source: SongsSource,
    libraryRepository: LibraryRepository,
) : SongsViewModel() {
    override val uiState: StateFlow<ListUiState<Song>> = when (source) {
        is SongsSource.Album -> libraryRepository.observeSongsByAlbum(source.id)
        is SongsSource.Artist -> libraryRepository.observeSongsByArtist(source.id)
    }
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(source: SongsSource): SongsViewModelImpl
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
