package com.eduardoflores.rolabox.device.playback.ui.impl

import kotlin.time.Duration

/** What the bar of Now Playing shows (ADR-018, rule 8). Scrub is a mode of the screen, not a destination. */
internal enum class NowPlayingMode {
    /** The time of the song. */
    Time,

    /** Turning moves the position. */
    Scrub,

    /** The volume, for a moment after it changed. */
    Volume,
}

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
     * between 0:00 and [duration]. [mode] says what the bar and the corner of the screen show, and [volume] is
     * the level from 0 to 100 that the bar shows in [NowPlayingMode.Volume].
     */
    data class Playing(
        val title: String,
        val artist: String,
        val album: String,
        val number: Int,
        val count: Int,
        val position: Duration,
        val duration: Duration,
        val mode: NowPlayingMode = NowPlayingMode.Time,
        val volume: Int = 0,
    ) : NowPlayingUiState {
        /** How full the volume is, from 0 to 1. */
        val volumeFraction: Float
            get() = volume.coerceIn(MIN_VOLUME, MAX_VOLUME) / MAX_VOLUME.toFloat()

        /** How far into the song it is, from 0 to 1. A song without length is at 0. */
        val progress: Float
            get() = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    }
}

private const val MIN_VOLUME = 0
private const val MAX_VOLUME = 100
