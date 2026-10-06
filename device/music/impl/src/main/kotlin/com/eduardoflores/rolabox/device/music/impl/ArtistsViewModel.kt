package com.eduardoflores.rolabox.device.music.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.Artist
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Artists screen shows (ADR-021). [DefaultArtistsViewModel] implements it. It has no
 * actions: the wheel is the screen's.
 */
internal abstract class ArtistsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Artist>>
}
