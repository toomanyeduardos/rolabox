package com.eduardoflores.rolabox.device.playback.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlin.time.Duration

/**
 * The actions on [PlaybackState]. Only [play], [pause] and [resume] change whether the loaded song is playing:
 * every other action leaves a paused song paused.
 */
interface PlaybackController {
    /**
     * Loads the whole list of [source] and starts playing at [from], at 0:00, in place of what was loaded.
     * Nothing changes on a `Left`.
     */
    suspend fun play(source: PlaybackSource, from: SongId): Either<PlaybackError, Unit>

    /** Pauses the loaded song where it is. Does nothing when it is paused, or when nothing is loaded. */
    suspend fun pause()

    /** Plays the loaded song from where it was paused. Does nothing when it is playing, or when nothing is loaded. */
    suspend fun resume()

    /**
     * Moves the position, kept between 0:00 and the duration of the current song. Does nothing when nothing
     * is loaded.
     */
    suspend fun seekTo(position: Duration)

    /**
     * Seeks in the current song towards [direction], faster than it plays, until [stopSeeking]. The position
     * stops at 0:00 and at the duration of the song, and the song doesn't end while it is being sought. Does
     * nothing when nothing is loaded.
     */
    suspend fun startSeeking(direction: SeekDirection)

    /** Ends [startSeeking]: the song goes on from where the seek left it. Does nothing when nothing is being sought. */
    suspend fun stopSeeking()

    /** Starts the next song at 0:00. Does nothing on the last song, or when nothing is loaded. */
    suspend fun next()

    /**
     * What ⏮ does: starts the loaded song again at 0:00 when it is past its first seconds, and the previous
     * song at 0:00 otherwise. The first song has no previous one, and starts again wherever it is. The
     * threshold is the playback area's (ADR-018, rule 17). Does nothing when nothing is loaded.
     */
    suspend fun previous()

    /** Sets the volume, kept between 0 and 100. */
    suspend fun setVolume(level: Int)
}
