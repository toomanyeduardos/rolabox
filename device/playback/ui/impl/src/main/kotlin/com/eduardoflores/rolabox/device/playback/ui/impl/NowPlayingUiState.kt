package com.eduardoflores.rolabox.device.playback.ui.impl

import kotlin.time.Duration

/** What Now Playing shows: nothing yet, that nothing is loaded, or the loaded song. */
internal sealed interface NowPlayingUiState {
    data object Loading : NowPlayingUiState

    /**
     * Nothing is loaded. The device pops the screen when the queue ends, so the user only meets this if
     * the state is lost.
     */
    data object NothingLoaded : NowPlayingUiState

    /**
     * The loaded song. [number] is its place in the queue from 1, out of [count]. [position] is kept
     * between 0:00 and [duration]. [tag] names the mode in the corner of the screen, and is `null` in normal mode.
     */
    data class Playing(
        val title: String,
        val artist: String,
        val album: String,
        val number: Int,
        val count: Int,
        val position: Duration,
        val duration: Duration,
        val tag: String? = null,
    ) : NowPlayingUiState {
        /** How far into the song it is, from 0 to 1. A song without length is at 0. */
        val progress: Float
            get() = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    }
}
