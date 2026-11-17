package com.eduardoflores.rolabox.device.podcasts.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import java.time.Clock
import javax.inject.Inject
import kotlin.time.Duration

/** Positions, and the rule of played, which is the podcasts part's alone (ADR-031, rule 6). */
internal class DefaultEpisodeProgressRepository @Inject constructor(
    private val episodes: PodcastsLocalDataSource,
    private val progress: EpisodeProgressLocalDataSource,
    private val clock: Clock,
) : EpisodeProgressRepository {
    override suspend fun getPosition(episodeId: EpisodeId): Either<PodcastError, Duration> =
        episode(episodeId).map { it.position }

    override suspend fun setPosition(
        episodeId: EpisodeId,
        position: Duration,
        length: Duration?,
    ): Either<PodcastError, Unit> = episode(episodeId).flatMap { episode ->
        val stored = position.coerceAtLeast(Duration.ZERO)
        val playerLength = length ?: episode.progress?.playerLength
        // Played stays played: a position back at the start doesn't clear it.
        val played = episode.progress?.isPlayed == true || reachesPlayed(stored, playerLength ?: episode.feedLength)
        progress.setProgress(episodeId, ProgressRecord(stored, played, playerLength, clock.instant()))
    }

    override suspend fun setEnded(episodeId: EpisodeId, length: Duration?): Either<PodcastError, Unit> =
        episode(episodeId).flatMap { episode ->
            val playerLength = length ?: episode.progress?.playerLength
            val end = playerLength ?: episode.feedLength ?: episode.position
            progress.setProgress(episodeId, ProgressRecord(end, isPlayed = true, playerLength, clock.instant()))
        }

    private suspend fun episode(episodeId: EpisodeId): Either<PodcastError, EpisodeRecord> =
        episodes.getEpisode(episodeId).flatMap { it?.right() ?: PodcastError.EpisodeNotFound(episodeId).left() }
}
