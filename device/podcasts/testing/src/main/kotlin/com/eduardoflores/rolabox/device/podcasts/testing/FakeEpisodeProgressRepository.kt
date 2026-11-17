package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Positions that tests set in [positions] and read back. It keeps what playback stores, and doesn't decide played:
 * [ended] records the episodes that played to their end. [error] makes every call return it.
 */
@Singleton
class FakeEpisodeProgressRepository @Inject constructor() : EpisodeProgressRepository {
    val positions = mutableMapOf<EpisodeId, Duration>()

    /** The last length each episode's position was stored with. */
    val lengths = mutableMapOf<EpisodeId, Duration?>()

    val ended = mutableSetOf<EpisodeId>()

    var error: PodcastError? = null

    override suspend fun getPosition(episodeId: EpisodeId): Either<PodcastError, Duration> =
        error?.left() ?: (positions[episodeId] ?: Duration.ZERO).right()

    override suspend fun setPosition(
        episodeId: EpisodeId,
        position: Duration,
        length: Duration?,
    ): Either<PodcastError, Unit> = error?.left() ?: run {
        positions[episodeId] = position
        lengths[episodeId] = length
        Unit.right()
    }

    override suspend fun setEnded(episodeId: EpisodeId, length: Duration?): Either<PodcastError, Unit> =
        error?.left() ?: run {
            ended += episodeId
            length?.let { positions[episodeId] = it }
            lengths[episodeId] = length
            Unit.right()
        }
}
