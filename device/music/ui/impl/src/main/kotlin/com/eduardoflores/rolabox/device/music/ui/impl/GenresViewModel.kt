package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.Genre
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Genres screen shows: every genre of the library, by name, with Unknown Genre last (ADR-021).
 * [GenresViewModelImpl] implements it. It has no actions: the wheel is the screen's.
 */
internal abstract class GenresViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Genre>>
}
