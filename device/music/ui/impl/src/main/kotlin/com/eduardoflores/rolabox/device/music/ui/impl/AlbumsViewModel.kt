package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.Album
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Albums screen shows: every album of the library, by title (ADR-021). [AlbumsViewModelImpl] implements
 * it. It has no actions: the wheel is the screen's.
 */
internal abstract class AlbumsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Album>>
}
