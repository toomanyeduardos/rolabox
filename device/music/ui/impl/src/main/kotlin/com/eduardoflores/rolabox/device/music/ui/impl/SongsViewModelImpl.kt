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
import kotlinx.coroutines.flow.flow
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

    // A name that can't be read leaves the header empty: the list already says the library can't be read.
    override val title: StateFlow<String?> = flow {
        emit(
            when (source) {
                is SongsSource.Album -> libraryRepository.getAlbum(source.id).getOrNull()?.title
                is SongsSource.Artist -> libraryRepository.getArtist(source.id).getOrNull()?.name
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @AssistedFactory
    interface Factory {
        fun create(source: SongsSource): SongsViewModelImpl
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
