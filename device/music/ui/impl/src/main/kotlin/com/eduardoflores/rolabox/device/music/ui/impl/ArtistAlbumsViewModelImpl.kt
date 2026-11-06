package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.GenreId
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The artist is the one of the entry's key, so it is given when the entry creates the ViewModel, with the genre when
 * the artist was picked in one: then only their albums with a song in it are listed.
 */
@HiltViewModel(assistedFactory = ArtistAlbumsViewModelImpl.Factory::class)
internal class ArtistAlbumsViewModelImpl @AssistedInject constructor(
    @Assisted(ARTIST) artistId: Long,
    @Assisted(GENRE) genreId: Long?,
    libraryRepository: LibraryRepository,
) : ArtistAlbumsViewModel() {
    override val uiState: StateFlow<ListUiState<Album>> = if (genreId == null) {
        libraryRepository.observeAlbumsByArtist(ArtistId(artistId))
    } else {
        libraryRepository.observeAlbumsByGenreAndArtist(GenreId(genreId), ArtistId(artistId))
    }
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    // A name that can't be read leaves the header empty: the list already says the library can't be read.
    override val title: StateFlow<ListTitle?> =
        flow {
            emit(
                libraryRepository.getArtist(ArtistId(artistId)).getOrNull()?.let {
                    ListTitle.Artist(it.name, it.isVarious)
                },
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @AssistedFactory
    interface Factory {
        // Both are numbers once compiled, so Hilt tells them apart by name.
        fun create(@Assisted(ARTIST) artistId: Long, @Assisted(GENRE) genreId: Long?): ArtistAlbumsViewModelImpl
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ARTIST = "artist"
        const val GENRE = "genre"
    }
}
