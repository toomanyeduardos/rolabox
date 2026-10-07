package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import kotlinx.coroutines.flow.StateFlow

/** Which songs a Songs screen lists: the ones on an album, or all of an artist's. */
internal sealed interface SongsSource {
    data class Album(val id: AlbumId) : SongsSource

    data class Artist(val id: ArtistId) : SongsSource
}

/** The songs of the Songs screen, whichever its [SongsSource] (ADR-021). [SongsViewModelImpl] implements it. */
internal abstract class SongsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Song>>
}
