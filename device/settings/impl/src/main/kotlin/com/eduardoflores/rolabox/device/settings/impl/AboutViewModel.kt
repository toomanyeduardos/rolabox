package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/** How many songs the library has, as About shows it. */
internal sealed interface SongCount {
    data object Loading : SongCount

    data class Loaded(val count: Int) : SongCount

    /** The library can't be read, and About says so (ADR-018: a screen says what it can't show). */
    data object Unavailable : SongCount
}

internal data class AboutUiState(val version: String, val songs: SongCount = SongCount.Loading)

/**
 * What About shows (ADR-021). The wheel does nothing on it but MENU, which is the host's.
 * [AboutViewModelImpl] implements it.
 */
internal abstract class AboutViewModel : ViewModel() {
    abstract val uiState: StateFlow<AboutUiState>
}
