package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The state of Now Playing and what the wheel does on it (ADR-021). [NowPlayingViewModelImpl] implements it.
 */
internal abstract class NowPlayingViewModel : ViewModel() {
    abstract val uiState: StateFlow<NowPlayingUiState>

    /**
     * The wheel turned by [steps], positive clockwise. Moves the marker in scrub, and changes the volume
     * otherwise.
     */
    abstract fun onTurn(steps: Int)

    /** Center was pressed: enters scrub, or accepts it when it is already on, and seeks to the marker. */
    abstract fun onCenter()

    /** MENU was pressed in scrub: leaves it without seeking. */
    abstract fun onMenu()

    /** The app went to the background: leaves scrub without seeking, so its wait never seeks unseen. */
    abstract fun onBackground()
}
