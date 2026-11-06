package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The source is the one of the entry's key, so it is given when the entry creates the ViewModel. */
@HiltViewModel(assistedFactory = ArtistsViewModelImpl.Factory::class)
internal class ArtistsViewModelImpl @AssistedInject constructor(
    @Assisted source: ArtistsSource,
    libraryRepository: LibraryRepository,
) : ArtistsViewModel() {
    override val uiState: StateFlow<ListUiState<Artist>> = when (source) {
        ArtistsSource.All -> libraryRepository.observeArtists()
        is ArtistsSource.Genre -> libraryRepository.observeArtistsByGenre(source.id)
    }
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    // A name that can't be read leaves the header empty: the list already says the library can't be read.
    override val title: StateFlow<ListTitle?> = flow {
        emit(
            when (source) {
                ArtistsSource.All -> ListTitle.AllArtists

                is ArtistsSource.Genre -> libraryRepository.getGenre(source.id).getOrNull()?.let {
                    ListTitle.Genre(it.name)
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @AssistedFactory
    interface Factory {
        fun create(source: ArtistsSource): ArtistsViewModelImpl
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
