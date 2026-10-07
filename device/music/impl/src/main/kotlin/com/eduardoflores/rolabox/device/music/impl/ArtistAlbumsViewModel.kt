package com.eduardoflores.rolabox.device.music.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.Album
import kotlinx.coroutines.flow.StateFlow

/**
 * The albums of one artist, for the screen after Artists (ADR-021). "All Songs" is not here: it is a
 * row the screen adds before these. [ArtistAlbumsViewModelImpl] implements it.
 */
internal abstract class ArtistAlbumsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<Album>>
}
