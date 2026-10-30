package com.eduardoflores.rolabox.device.playback.api

import arrow.core.Either
import kotlin.time.Duration

/**
 * The actions on [PlaybackState]. Only [play], [pause] and [resume] change whether the loaded item is playing:
 * every other action leaves a paused item paused.
 */
interface PlaybackController {
    /**
     * Loads the whole list of [source] and starts playing at [from], at 0:00, in place of what was loaded.
     * Nothing changes on a `Left`.
     */
    suspend fun play(source: PlaybackSource, from: PlaybackItemId): Either<PlaybackError, Unit>

    /** Pauses the loaded item where it is. Does nothing when it is paused, or when nothing is loaded. */
    suspend fun pause()

    /** Plays the loaded item from where it was paused. Does nothing when it is playing, or when nothing is loaded. */
    suspend fun resume()

    /**
     * Moves the position, kept between 0:00 and the duration of the current item. Does nothing when nothing
     * is loaded.
     */
    suspend fun seekTo(position: Duration)

    /**
     * Seeks in the current item towards [direction], faster than it plays, until [stopSeeking]. The position
     * stops at 0:00 and at the duration of the item, and the item doesn't end while it is being sought. Does
     * nothing when nothing is loaded.
     */
    suspend fun startSeeking(direction: SeekDirection)

    /** Ends [startSeeking]: the item goes on from where the seek left it. Does nothing when nothing is being sought. */
    suspend fun stopSeeking()

    /** Starts the next item at 0:00. Does nothing on the last item, or when nothing is loaded. */
    suspend fun next()

    /**
     * What ⏮ does: starts the loaded item again at 0:00 when it is past its first seconds, and the previous
     * item at 0:00 otherwise. The first item has no previous one, and starts again wherever it is. The
     * threshold is the playback area's (ADR-018, rule 17). Does nothing when nothing is loaded.
     */
    suspend fun previous()

    /**
     * Moves the volume by [steps] of the phone's media volume, up when positive and down when negative, and
     * stops at its ends. The phone has its own number of steps, so one step isn't one point of
     * [PlaybackState.volume] (ADR-023).
     */
    suspend fun changeVolume(steps: Int)
}
