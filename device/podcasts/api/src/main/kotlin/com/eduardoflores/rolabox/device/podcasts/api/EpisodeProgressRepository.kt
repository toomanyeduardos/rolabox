package com.eduardoflores.rolabox.device.podcasts.api

import arrow.core.Either
import kotlin.time.Duration

/**
 * Where each episode is, kept by the podcasts part and written by playback (ADR-031, rule 7). It decides played: an
 * episode is played once its position reaches 90% of its length, or it ends, and stays played (rule 6).
 */
interface EpisodeProgressRepository {
    /** The position the episode resumes from: zero when it was never started. */
    suspend fun getPosition(episodeId: EpisodeId): Either<PodcastError, Duration>

    /**
     * Stores the [position], with the [length] the player knows, which replaces the feed's. With no length known,
     * neither the player's nor the feed's, a position never makes the episode played: only its end does.
     */
    suspend fun setPosition(episodeId: EpisodeId, position: Duration, length: Duration?): Either<PodcastError, Unit>

    /** The episode played to its end: it is played, and its position is the end. */
    suspend fun setEnded(episodeId: EpisodeId, length: Duration?): Either<PodcastError, Unit>
}
