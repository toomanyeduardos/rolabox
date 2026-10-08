package com.eduardoflores.rolabox.device.playback.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlin.time.Duration

/** The actions on [PlaybackState]. */
interface PlaybackController {
    /**
     * Loads the whole list of [source] and starts at [from], at 0:00, in place of what was loaded.
     * Nothing changes on a `Left`.
     */
    suspend fun play(source: PlaybackSource, from: SongId): Either<PlaybackError, Unit>

    /**
     * Moves the position, kept between 0:00 and the duration of the current song. Does nothing when nothing
     * is loaded.
     */
    suspend fun seekTo(position: Duration)

    /** Starts the next song at 0:00. Does nothing on the last song, or when nothing is loaded. */
    suspend fun next()

    /** Starts the previous song at 0:00. Does nothing on the first song, or when nothing is loaded. */
    suspend fun previous()

    /** Sets the volume, kept between 0 and 100. */
    suspend fun setVolume(level: Int)
}
