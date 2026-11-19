package com.eduardoflores.rolabox.device.podcasts.local.impl

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkSource
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FeedWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FollowedFeed
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The shows, episodes and follows in Room. */
@Suppress("TooManyFunctions") // One function for each of the source's.
internal class RoomPodcastsLocalDataSource @Inject constructor(
    private val dao: PodcastsDao,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val report: NonFatalReport,
) : PodcastsLocalDataSource {
    override fun observeOwners(): Flow<Either<PodcastError, List<Owner>>> =
        dao.observeOwners().map { rows -> rows.map { Owner(OwnerId(it.ownerKey), it.name) } }.asRead()

    override fun observeShowsByOwner(ownerId: OwnerId): Flow<Either<PodcastError, List<Show>>> =
        dao.observeShowsByOwner(ownerId.value).map { rows -> rows.map(ShowRow::toShow) }.asRead()

    override fun observeShow(showId: ShowId): Flow<Either<PodcastError, Show?>> =
        dao.observeShow(showId.value).map { it?.toShow() }.asRead()

    override suspend fun getShow(showId: ShowId): Either<PodcastError, Show?> =
        catchNamed(::toError) { dao.getShow(showId.value)?.toShow() }

    override fun observeShowByDirectoryId(directoryId: DirectoryId): Flow<Either<PodcastError, Show?>> =
        dao.observeShowByDirectoryId(directoryId.value).map { it?.toShow() }.asRead()

    override suspend fun getEpisodeId(directoryId: DirectoryId, itemKey: String): Either<PodcastError, EpisodeId?> =
        catchNamed(::toError) { dao.episodeIdOf(directoryId.value, itemKey)?.let(::EpisodeId) }

    override fun observeEpisodesByShow(showId: ShowId): Flow<Either<PodcastError, List<EpisodeRecord>>> =
        dao.observeEpisodesByShow(showId.value).map { rows -> rows.map(EpisodeRow::toRecord) }.asRead()

    override fun observeEpisode(episodeId: EpisodeId): Flow<Either<PodcastError, EpisodeRecord?>> =
        dao.observeEpisode(episodeId.value).map { it?.toRecord() }.asRead()

    override suspend fun getEpisode(episodeId: EpisodeId): Either<PodcastError, EpisodeRecord?> =
        catchNamed(::toError) { dao.getEpisode(episodeId.value)?.toRecord() }

    override suspend fun setFollow(showId: ShowId, followed: Boolean, updatedAt: Instant): Either<PodcastError, Unit> =
        catchNamed(::toError) { dao.setFollow(showId.value, followed, updatedAt.toEpochMilli()) }
            .flatMap { found -> if (found) Unit.right() else PodcastError.ShowNotFound(showId).left() }

    override suspend fun addEpisode(
        show: ShowWrite,
        episode: EpisodeWrite,
        addedAt: Instant,
    ): Either<PodcastError, EpisodeId> = catchNamed(::toError) {
        EpisodeId(dao.addEpisode(show.toEntity(addedAt), episode.toEntity(addedAt)))
    }

    override suspend fun follow(
        show: ShowWrite,
        episodes: List<EpisodeWrite>,
        followedAt: Instant,
    ): Either<PodcastError, ShowId> = catchNamed(::toError) {
        val follow = FollowEntity(show.directoryId.value, show.feedUrl, true, followedAt.toEpochMilli())
        ShowId(dao.follow(show.toEntity(followedAt), episodes.map { it.toEntity(followedAt) }, follow))
    }

    override suspend fun getArtworkSource(episodeId: EpisodeId): Either<PodcastError, ArtworkSource?> =
        catchNamed(::toError) {
            dao.getArtworkSource(episodeId.value)?.let { row ->
                ArtworkSource(
                    episodeId = EpisodeId(row.episodeId),
                    showId = ShowId(row.showId),
                    itemKey = row.itemKey,
                    feedUrl = row.feedUrl,
                    episodeImageUrl = row.episodeImageUrl,
                    showImageUrl = row.showImageUrl,
                )
            }
        }

    override suspend fun setImageUrls(
        showId: ShowId,
        showImageUrl: String?,
        episodeImageUrls: Map<String, String>,
    ): Either<PodcastError, Unit> = catchNamed(::toError) {
        dao.setImageUrls(showId.value, showImageUrl, episodeImageUrls)
    }

    override suspend fun getFollowedFeeds(): Either<PodcastError, List<FollowedFeed>> = catchNamed(::toError) {
        dao.getFollowedFeeds().map { row ->
            FollowedFeed(
                directoryId = DirectoryId(row.directoryId),
                feedUrl = row.feedUrl,
                show = row.showId?.let { dao.getShow(it)?.toShow() },
                etag = row.etag,
                lastModified = row.lastModified,
            )
        }
    }

    override suspend fun applyFeed(feed: FeedWrite, readAt: Instant): Either<PodcastError, ShowId?> =
        catchNamed(::toError) {
            val show = feed.show.toEntity(readAt).copy(
                imageUrl = feed.imageUrl,
                etag = feed.etag,
                lastModified = feed.lastModified,
            )
            dao.applyFeed(show, feed.episodes.map { it.toEntity(readAt) }, readAt.toEpochMilli())?.let(::ShowId)
        }

    override suspend fun applyFeedNotModified(
        directoryId: DirectoryId,
        feedUrl: String,
        readAt: Instant,
    ): Either<PodcastError, Unit> = catchNamed(::toError) {
        dao.applyFeedNotModified(directoryId.value, feedUrl, readAt.toEpochMilli())
    }

    private fun <T> Flow<T>.asRead(): Flow<Either<PodcastError, T>> = flowOn(ioDispatcher).catchNamed(::toError)

    private fun toError(error: Throwable): PodcastError? = toPodcastError(error, report)
}

/**
 * The podcasts database's exceptions as errors. Every database failure is reported (ADR-031, Errors), but a full
 * disk, which is the phone's state and an expected outcome (ADR-030, rule 6). The kind names the exception's class,
 * the backend's code, since the report carries neither the exception's class nor its message (ADR-030, rule 5):
 * without it, a broken constraint, a failing disk and a locked database would share one stack trace and one kind.
 */
internal fun toPodcastError(error: Throwable, report: NonFatalReport): PodcastError? = when (error) {
    is SQLiteDatabaseCorruptException -> {
        report.report("StorageError.Corrupted Room", error)
        PodcastError.Storage(StorageError.Corrupted)
    }

    is SQLiteFullException -> PodcastError.Storage(StorageError.Unavailable)

    is SQLiteException -> {
        report.report("StorageError.Unavailable Room ${error.javaClass.simpleName}", error)
        PodcastError.Storage(StorageError.Unavailable)
    }

    else -> null
}
