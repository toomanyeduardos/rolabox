package com.eduardoflores.rolabox.device.podcasts.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError

/**
 * The progress of each episode, in a table apart from the episodes', so reading a feed again never touches it
 * (ADR-031, The database). It is read with its episode, in [EpisodeRecord.progress].
 */
interface EpisodeProgressLocalDataSource {
    /** Replaces the episode's progress. Returns [PodcastError.EpisodeNotFound] when there is no such episode. */
    suspend fun setProgress(episodeId: EpisodeId, progress: ProgressRecord): Either<PodcastError, Unit>
}
