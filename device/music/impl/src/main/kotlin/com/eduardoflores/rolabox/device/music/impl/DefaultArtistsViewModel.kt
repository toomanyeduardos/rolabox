package com.eduardoflores.rolabox.device.music.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class DefaultArtistsViewModel @Inject constructor(libraryRepository: LibraryRepository) :
    ArtistsViewModel() {
    override val uiState: StateFlow<ListUiState<Artist>> = libraryRepository.observeArtists()
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
