package com.eduardoflores.rolabox.device.playback.api

import arrow.core.Either
import kotlin.time.Duration

/**
 * The actions on [PlaybackState]. Only [play], [pause] and [resume] change whether the loaded item is playing:
 * every other action leaves a paused item paused.
 */
interface PlaybackController {
    /**
     * Loads the whole list of [source] and starts playing at [from], in place of what was loaded. It starts at 0:00,
     * unless [resume] is `true` and the item keeps a position of its own, as an episode does: it then starts from where
     * it was left (ADR-031). A song always starts at 0:00. Nothing changes on a `Left`.
     */
    suspend fun play(source: PlaybackSource, from: PlaybackItemId, resume: Boolean = false): Either<PlaybackError, Unit>

    /**
     * Shuffle Songs (ADR-028): loads the whole list of [source] in a random order, by song, and starts playing a random
     * item of it at 0:00, in place of what was loaded. The queue stays shuffled whatever the Shuffle setting says, and
     * Repeat applies to it. Nothing changes on a `Left`, which [PlaybackError.NothingToPlay] is for a list with no
     * items.
     */
    suspend fun shuffle(source: PlaybackSource): Either<PlaybackError, Unit>

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

    /**
     * What ⏭ does: starts the next item at 0:00, and does nothing on the last item. During an episode it skips
     * forward 15 s instead, and in its last 15 s goes to its end, which ends it (ADR-031, rule 11; ADR-044). Does
     * nothing when nothing is loaded.
     */
    suspend fun next()

    /**
     * What ⏮ does: starts the loaded item again at 0:00 when it is past its first seconds, and the previous
     * item at 0:00 otherwise. The first item has no previous one, and starts again wherever it is. The
     * threshold is the playback area's (ADR-018, rule 17). During an episode it skips back 15 s instead, and stops at
     * 0:00 (ADR-031, rule 11; ADR-044). Does nothing when nothing is loaded.
     */
    suspend fun previous()

    /**
     * Moves the volume by [steps] of the phone's media volume, up when positive and down when negative, and
     * stops at its ends. The phone has its own number of steps, so one step isn't one point of
     * [PlaybackState.volume] (ADR-023).
     */
    suspend fun changeVolume(steps: Int)
}
