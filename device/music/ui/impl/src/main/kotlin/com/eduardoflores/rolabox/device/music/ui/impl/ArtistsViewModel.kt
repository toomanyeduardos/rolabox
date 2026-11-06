package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.GenreId
import kotlinx.coroutines.flow.StateFlow

/** Which artists an Artists screen lists: every artist of the library, or those with a song in a genre. */
internal sealed interface ArtistsSource {
    data object All : ArtistsSource

    data class Genre(val id: GenreId) : ArtistsSource
}

/**
 * What an Artists screen shows, whichever its [ArtistsSource] (ADR-021). [ArtistsViewModelImpl] implements it. It has
 * no actions: the wheel is the screen's.
 */
internal abstract class ArtistsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Artist>>

    /** The screen's title: Artists, or the genre's name. `null` until it is read, and when it can't be. */
    abstract val title: StateFlow<ListTitle?>
}
