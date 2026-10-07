package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The artist is the one of the entry's key, so it is given when the entry creates the ViewModel. */
@HiltViewModel(assistedFactory = ArtistAlbumsViewModelImpl.Factory::class)
internal class ArtistAlbumsViewModelImpl @AssistedInject constructor(
    @Assisted artistId: Long,
    libraryRepository: LibraryRepository,
) : ArtistAlbumsViewModel() {
    override val uiState: StateFlow<ListUiState<Album>> = libraryRepository.observeAlbumsByArtist(ArtistId(artistId))
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(artistId: Long): ArtistAlbumsViewModelImpl
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
