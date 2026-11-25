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
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.api.ShowInLibrary
import com.eduardoflores.rolabox.device.podcasts.api.ShowKind
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckScheduler
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile

/** The podcasts as the database holds them (ADR-031, rule 4), with each episode's download from playback. */
@Suppress("TooManyFunctions") // One for each function of the repository.
internal class DefaultPodcastsRepository @Inject constructor(
    private val local: PodcastsLocalDataSource,
    private val downloads: EpisodeDownloads,
    private val clock: Clock,
    private val followCheck: FollowCheckScheduler,
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
                list.mapNotNull { record ->
                    val episode = record.toEpisode(downloaded[record.id])
                    // Follow Off, and an episode that left its feed, list only what has a download, done or in
                    // progress (ADR-031, The database).
                    val listedWithoutDownload = found.isFollowed && record.leftFeedAt == null
                    episode.takeIf { listedWithoutDownload || it.download != DownloadState.NotDownloaded }
                }
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
        local.setFollow(showId, followed, clock.instant()).onRight {
            // Turning Follow On reads the show's feed at once, so its new episodes show (ADR-031, Follow checks).
            if (followed) local.getShow(showId).getOrNull()?.let { show -> followCheck.checkShow(show.directoryId) }
        }

    // The feed's order clears the choice, so a show whose feed changes its kind follows it (ADR-043). Either is a
    // choice the user made now, to sync.
    override suspend fun setEpisodeOrder(showId: ShowId, order: EpisodeOrder): Either<PodcastError, Unit> =
        local.getShow(showId).flatMap { show ->
            if (show == null) return@flatMap PodcastError.ShowNotFound(showId).left()
            val choice = when {
                order == show.feedEpisodeOrder -> EpisodeOrderChoice.Feed
                order == EpisodeOrder.Oldest -> EpisodeOrderChoice.Oldest
                else -> EpisodeOrderChoice.Newest
            }
            local.setEpisodeOrder(showId, choice, clock.instant())
        }

    override suspend fun addEpisode(
        show: DirectoryShow,
        episode: DirectoryEpisode,
        kind: ShowKind?,
    ): Either<PodcastError, EpisodeId> =
        // Until its feed is read, a show's owner is the directory's creator (ADR-031, The directory).
        local.addEpisode(show.toWrite(owner = show.creator, kind = kind), episode.toWrite(), clock.instant())

    override suspend fun follow(feed: ShowFeed): Either<PodcastError, ShowId> = local.follow(
        feed.show.toWrite(owner = feed.ownerName, kind = feed.kind),
        feed.episodes.map { it.toWrite() },
        clock.instant(),
    )

    override suspend fun findShow(show: DirectoryShow): Either<PodcastError, Show?> =
        local.observeShowByDirectoryId(show.directoryId).first()

    override suspend fun findEpisode(show: DirectoryShow, episode: DirectoryEpisode): Either<PodcastError, EpisodeId?> =
        local.getEpisodeId(show.directoryId, episode.itemKey)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeInLibrary(feed: ShowFeed): Flow<Either<PodcastError, ShowInLibrary?>> =
        local.observeShowByDirectoryId(feed.show.directoryId).flatMapLatest { found ->
            val show = found.getOrNull()
                ?: return@flatMapLatest flowOf<Either<PodcastError, ShowInLibrary?>>(found.map { null })
            // Every episode the library has of the show, whatever its Follow says: the feed decides what is listed.
            combine(local.observeEpisodesByShow(show.id), downloads.observe()) { records, downloaded ->
                records.map<ShowInLibrary?> { list ->
                    val byItemKey = list.associateBy { it.itemKey }
                    ShowInLibrary(
                        show = show,
                        episodes = feed.episodes.map { episode ->
                            byItemKey[episode.itemKey]?.let { it.toEpisode(downloaded[it.id]) }
                        },
                    )
                }
            }
        }.endingAtFirstLeft()

    private fun notFound(episodeId: EpisodeId): Either<PodcastError, Episode> =
        PodcastError.EpisodeNotFound(episodeId).left()
}

private fun DirectoryShow.toWrite(owner: String?, kind: ShowKind?): ShowWrite {
    val name = cleanedName(owner)
    return ShowWrite(
        directoryId = directoryId,
        feedUrl = feedUrl,
        title = title,
        ownerName = name,
        ownerKey = ownerIdOf(name),
        titleSortKey = sortKeyOf(title),
        ownerSortKey = name?.let(::sortKeyOf),
        kind = kind,
    )
}

private fun DirectoryEpisode.toWrite() = EpisodeWrite(
    itemKey = itemKey,
    guid = guid,
    title = title,
    publishedAt = publishedAt,
    length = length,
    description = description,
    audioUrl = audioUrl,
    audioSizeBytes = audioSizeBytes,
    audioType = audioType,
)

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
