package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The state of Now Playing and what the wheel does on it (ADR-021). [NowPlayingViewModelImpl] implements it.
 */
internal abstract class NowPlayingViewModel : ViewModel() {
    abstract val uiState: StateFlow<NowPlayingUiState>

    /**
     * The wheel turned by [steps], positive clockwise. Moves the position in scrub, and changes the volume
     * otherwise.
     */
    abstract fun onTurn(steps: Int)

    /** Center was pressed: enters scrub, or leaves it when it is already on. */
    abstract fun onCenter()

    /** The system's back: leaves scrub. */
    abstract fun onBack()
}
