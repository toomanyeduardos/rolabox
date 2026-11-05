package com.eduardoflores.rolabox.device.playback.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import kotlin.time.Duration

/**
 * Where to carry on from after the app was killed (ADR-023): the list that was played, not its songs, so its order is
 * the list's. [index] is where [item] was in the play order: the [PlayOrder]'s when the queue was shuffled, and the
 * list's otherwise (ADR-028). It is where the next one is once [item] is gone.
 */
data class ResumePoint(val source: PlaybackSource, val item: PlaybackItemId, val index: Int, val position: Duration)

/** What made a queue's order shuffled (ADR-028): the Shuffle setting, by songs or by albums, or Shuffle Songs. */
enum class ShuffledBy { Songs, Albums, ShuffleSongs }

/**
 * A shuffled queue's play order (ADR-028): the items of the round it is in, in the order they play, and what shuffled
 * them. A queue in its list's order has none, since the library gives that order again.
 */
data class PlayOrder(val items: List<PlaybackItemId>, val shuffledBy: ShuffledBy)

/** What playback keeps on the phone to restore after the app is killed (ADR-023, rule 11). It is never synced. */
interface PlaybackLocalDataSource {
    /** The stored point, or `null` when there is none, or it can't be read as one. */
    suspend fun resumePoint(): Either<StorageError, ResumePoint?>

    suspend fun store(point: ResumePoint): Either<StorageError, Unit>

    /**
     * The stored order, or `null` when there is none, or it can't be read as one. It is kept apart from the point, and
     * written only when it changes: a whole library's order is too large to write as often as the point (ADR-028).
     */
    suspend fun playOrder(): Either<StorageError, PlayOrder?>

    /** Stores [order], or that the queue plays in its list's order when it is `null`. */
    suspend fun storeOrder(order: PlayOrder?): Either<StorageError, Unit>

    /** Nothing is loaded, so nothing comes back: neither the point nor the order. */
    suspend fun clear(): Either<StorageError, Unit>
}
