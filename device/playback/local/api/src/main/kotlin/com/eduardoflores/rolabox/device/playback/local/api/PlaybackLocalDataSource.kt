package com.eduardoflores.rolabox.device.playback.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import kotlin.time.Duration

/**
 * Where to carry on from after the app was killed (ADR-023): the list that was played, not its songs, so its order is
 * the list's. [index] is where [item] was in that list, which is where the next one is once [item] is gone.
 */
data class ResumePoint(val source: PlaybackSource, val item: PlaybackItemId, val index: Int, val position: Duration)

/** What playback keeps on the phone to restore after the app is killed (ADR-023, rule 11). It is never synced. */
interface PlaybackLocalDataSource {
    /** The stored point, or `null` when there is none, or it can't be read as one. */
    suspend fun resumePoint(): Either<StorageError, ResumePoint?>

    suspend fun store(point: ResumePoint): Either<StorageError, Unit>

    /** Nothing is loaded, so nothing comes back. */
    suspend fun clear(): Either<StorageError, Unit>
}
