package com.eduardoflores.rolabox.device.playback.api

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * What is loaded, whether it is playing and how far it is. An item is loaded from the moment it starts until
 * its queue ends, and a loaded item is either playing or paused.
 *
 * Nothing is loaded until [PlaybackController.play] succeeds: [currentItem] is `null`, [queue] is empty
 * and [queueIndex] is `null`. The state lives above the screens, so it outlives each of them.
 */
interface PlaybackState {
    /** The item at [queueIndex], or `null` when nothing is loaded. */
    val currentItem: Flow<PlaybackItem?>

    /** Whether [currentItem] is playing, and not paused. `false` when nothing is loaded. */
    val isPlaying: Flow<Boolean>

    /**
     * How far into [currentItem] it is. It advances while the item plays and stays while it is paused.
     * [Duration.ZERO] when nothing is loaded.
     */
    val position: Flow<Duration>

    /** The whole list that was played, including the items before the one it started from. */
    val queue: Flow<List<PlaybackItem>>

    /** Where [currentItem] is in [queue], or `null` when nothing is loaded. */
    val queueIndex: Flow<Int?>

    /**
     * How many times an item has started at 0:00: when a row is played, when ⏮ or ⏭ starts an item, and when
     * an item ends and the next one starts. ⏮ can start the loaded item again, so [currentItem] alone doesn't
     * say that an item started. What belongs to one start of an item, such as a scrub, ends when this changes.
     */
    val startCount: Flow<Int>

    /** The phone's media volume, from 0 to 100 of its maximum. */
    val volume: Flow<Int>
}
