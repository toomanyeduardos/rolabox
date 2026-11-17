package com.eduardoflores.rolabox.device.podcasts.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile

/** The podcasts as the database holds them (ADR-031, rule 4), with each episode's download from playback. */
internal class DefaultPodcastsRepository @Inject constructor(
    private val local: PodcastsLocalDataSource,
    private val downloads: EpisodeDownloads,
    private val clock: Clock,
) : PodcastsRepository {
    override fun observeOwners(): Flow<Either<PodcastError, List<Owner>>> = local.observeOwners()

    override fun observeShowsByOwner(ownerId: OwnerId): Flow<Either<PodcastError, List<Show>>> =
        local.observeShowsByOwner(ownerId).map { shows ->
            // An owner is only the key its shows share: with none left, it isn't there.
            shows.flatMap { if (it.isEmpty()) PodcastError.OwnerNotFound(ownerId).left() else it.right() }
        }.endingAtFirstLeft()

    override fun observeShow(showId: ShowId): Flow<Either<PodcastError, Show>> = local.observeShow(showId).map { show ->
        show.flatMap { it?.right() ?: PodcastError.ShowNotFound(showId).left() }
    }.endingAtFirstLeft()

    override fun observeEpisodesByShow(showId: ShowId): Flow<Either<PodcastError, List<Episode>>> = combine(
        local.observeShow(showId),
        local.observeEpisodesByShow(showId),
        downloads.observe(),
    ) { show, records, downloaded ->
        show.flatMap { it?.right() ?: PodcastError.ShowNotFound(showId).left() }.flatMap { found ->
            records.map { list ->
                list.map { it.toEpisode(downloaded[it.id]) }
                    // Follow Off lists only what has a download, done or in progress (ADR-031, The database).
                    .filter { found.isFollowed || it.download != DownloadState.NotDownloaded }
            }
        }
    }.endingAtFirstLeft()

    override fun observeEpisode(episodeId: EpisodeId): Flow<Either<PodcastError, Episode>> =
        combine(local.observeEpisode(episodeId), downloads.observe()) { record, downloaded ->
            record.flatMap { it?.toEpisode(downloaded[episodeId])?.right() ?: notFound(episodeId) }
        }.endingAtFirstLeft()

    override suspend fun getEpisode(episodeId: EpisodeId): Either<PodcastError, Episode> =
        local.getEpisode(episodeId).flatMap { record ->
            record?.toEpisode(downloads.observe().first()[episodeId])?.right() ?: notFound(episodeId)
        }

    override suspend fun setFollowed(showId: ShowId, followed: Boolean): Either<PodcastError, Unit> =
        local.setFollow(showId, followed, clock.instant())

    override suspend fun addEpisode(show: DirectoryShow, episode: DirectoryEpisode): Either<PodcastError, EpisodeId> {
        // Until its feed is read, a show's owner is the directory's creator (ADR-031, The directory).
        val owner = cleanedName(show.creator)
        val showWrite = ShowWrite(
            directoryId = show.directoryId,
            feedUrl = show.feedUrl,
            title = show.title,
            ownerName = owner,
            ownerKey = ownerIdOf(owner),
            titleSortKey = sortKeyOf(show.title),
            ownerSortKey = owner?.let(::sortKeyOf),
        )
        val episodeWrite = EpisodeWrite(
            itemKey = episode.itemKey,
            guid = episode.guid,
            title = episode.title,
            publishedAt = episode.publishedAt,
            length = episode.length,
            description = episode.description,
            audioUrl = episode.audioUrl,
        )
        return local.addEpisode(showWrite, episodeWrite, clock.instant())
    }

    private fun notFound(episodeId: EpisodeId): Either<PodcastError, Episode> =
        PodcastError.EpisodeNotFound(episodeId).left()
}

private fun EpisodeRecord.toEpisode(download: DownloadState?): Episode = Episode(
    id = id,
    showId = showId,
    title = title,
    publishedAt = publishedAt,
    length = length,
    description = description,
    audioUrl = audioUrl,
    audioSizeBytes = audioSizeBytes,
    playedState = playedState,
    position = position,
    download = download ?: DownloadState.NotDownloaded,
)

private fun <E, T> Flow<Either<E, T>>.endingAtFirstLeft(): Flow<Either<E, T>> = transformWhile {
    emit(it)
    it.isRight()
}
