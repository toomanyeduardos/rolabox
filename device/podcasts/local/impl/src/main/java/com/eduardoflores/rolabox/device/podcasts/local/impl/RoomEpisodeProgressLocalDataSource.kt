package com.eduardoflores.rolabox.device.podcasts.local.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import javax.inject.Inject

/** The progress table in Room. */
internal class RoomEpisodeProgressLocalDataSource @Inject constructor(
    private val dao: ProgressDao,
    private val report: NonFatalReport,
) : EpisodeProgressLocalDataSource {
    override suspend fun setProgress(episodeId: EpisodeId, progress: ProgressRecord): Either<PodcastError, Unit> =
        catchNamed(::toError) {
            // Checked first, so a missing episode is its own error and not a broken foreign key.
            if (dao.hasEpisode(episodeId.value)) {
                dao.upsert(progress.toEntity(episodeId)).right()
            } else {
                PodcastError.EpisodeNotFound(episodeId).left()
            }
        }.flatMap { it }

    private fun toError(error: Throwable): PodcastError? = toPodcastError(error, report)
}
