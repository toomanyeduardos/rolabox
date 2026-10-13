package com.eduardoflores.rolabox.device.playback.api

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * What is loaded, whether it is playing and how far it is. An item is loaded from the moment it starts until
 * its queue ends, and a loaded item is either playing or paused.
 *
 * Nothing is loaded until [PlaybackController.play] succeeds: [currentSong] is `null`, [queue] is empty
 * and [queueIndex] is `null`. The state lives above the screens, so it outlives each of them.
 */
interface PlaybackState {
    /** The song at [queueIndex], or `null` when nothing is loaded. */
    val currentSong: Flow<PlaybackSong?>

    /** Whether [currentSong] is playing, and not paused. `false` when nothing is loaded. */
    val isPlaying: Flow<Boolean>

    /**
     * How far into [currentSong] it is. It advances while the song plays and stays while it is paused.
     * [Duration.ZERO] when nothing is loaded.
     */
    val position: Flow<Duration>

    /** The whole list that was played, including the songs before the one it started from. */
    val queue: Flow<List<PlaybackSong>>

    /** Where [currentSong] is in [queue], or `null` when nothing is loaded. */
    val queueIndex: Flow<Int?>

    /**
     * How many times a song has started at 0:00: when a row is played, when ⏮ or ⏭ starts a song, and when a
     * song ends and the next one starts. ⏮ can start the loaded song again, so [currentSong] alone doesn't
     * say that a song started. What belongs to one start of a song, such as a scrub, ends when this changes.
     */
    val startCount: Flow<Int>

    /** The volume, from 0 to 100. */
    val volume: Flow<Int>
}
