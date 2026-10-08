package com.eduardoflores.rolabox.device.playback.api

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * What is loaded and how far it is. An item is loaded from the moment it starts until its queue ends.
 *
 * Nothing is loaded until [PlaybackController.play] succeeds: [currentSong] is `null`, [queue] is empty
 * and [queueIndex] is `null`. The state lives above the screens, so it outlives each of them.
 */
interface PlaybackState {
    /** The song at [queueIndex], or `null` when nothing is loaded. */
    val currentSong: Flow<PlaybackSong?>

    /** How far into [currentSong] it is. [Duration.ZERO] when nothing is loaded. */
    val position: Flow<Duration>

    /** The whole list that was played, including the songs before the one it started from. */
    val queue: Flow<List<PlaybackSong>>

    /** Where [currentSong] is in [queue], or `null` when nothing is loaded. */
    val queueIndex: Flow<Int?>

    /** The volume, from 0 to 100. */
    val volume: Flow<Int>
}
