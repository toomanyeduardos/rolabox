package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The state of Now Playing (ADR-021). [NowPlayingViewModelImpl] implements it. It has no actions yet: scrub
 * and volume come with their modes.
 */
internal abstract class NowPlayingViewModel : ViewModel() {
    abstract val uiState: StateFlow<NowPlayingUiState>
}
