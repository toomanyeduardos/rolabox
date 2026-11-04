package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import kotlinx.coroutines.flow.StateFlow

/** Which songs a Songs screen lists: the ones on an album, all of an artist's, or every song of the library. */
internal sealed interface SongsSource {
    data class Album(val id: AlbumId) : SongsSource

    data class Artist(val id: ArtistId) : SongsSource

    /** Every song, by title, from Songs in the Music menu. */
    data object All : SongsSource
}

/** Whether the songs of [this] source are sorted by title, so the list shows the fast-scroll letter. */
internal val SongsSource.isSortedByName: Boolean
    get() = when (this) {
        is SongsSource.Album -> false
        is SongsSource.Artist, SongsSource.All -> true
    }

/** The songs of the Songs screen, whichever its [SongsSource] (ADR-021). [SongsViewModelImpl] implements it. */
internal abstract class SongsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Song>>

    /**
     * The screen's title: the album's name, the artist's for all of their songs, or Songs for every song. `null`
     * until it is read, and when it can't be.
     */
    abstract val title: StateFlow<ListTitle?>
}
