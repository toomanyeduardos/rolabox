package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkSource
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FeedWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FollowedFeed
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * The podcasts' database in memory, for the tests of the part's `:impl`. It keeps what the Room database keeps, with
 * the progress apart from the episodes and the follows apart from the shows, gives the ids in the order things are
 * added, and lists them in the database's orders. The contract test in `:device:podcasts:local:impl` runs the same
 * checks against this fake and the Room database. [readError] makes every call fail with `Left(Storage(error))`.
 */
@Suppress("TooManyFunctions") // One for each function of the two sources it fakes.
@Singleton
class FakePodcastsLocalDataSource @Inject constructor() :
    PodcastsLocalDataSource,
    EpisodeProgressLocalDataSource {
    private val state = MutableStateFlow(Stored())
    private val readErrorState = MutableStateFlow<StorageError?>(null)

    var readError: StorageError?
        get() = readErrorState.value
        set(value) {
            readErrorState.value = value
        }

    /** The follows as stored, by directory id, with when each was last set. */
    val follows: Map<DirectoryId, Pair<Boolean, Instant>>
        get() = state.value.follows

    /** The feed URL each follow keeps for sync, by directory id. */
    val followFeedUrls: Map<DirectoryId, String>
        get() = state.value.followFeedUrls

    /** The feed URL each show in the library is read from, by directory id. */
    val showFeedUrls: Map<DirectoryId, String>
        get() = state.value.shows.associate { it.write.directoryId to it.write.feedUrl }

    /** Stores a follow of a show that may not be in the library, as sync would (ADR-031, Syncing follows). */
    fun storeFollow(directoryId: DirectoryId, feedUrl: String, followed: Boolean, updatedAt: Instant) {
        state.update {
            it.copy(
                follows = it.follows + (directoryId to (followed to updatedAt)),
                followFeedUrls = it.followFeedUrls + (directoryId to feedUrl),
            )
        }
    }

    // Each owner's name is the spelling most of its shows have. Owners and shows are sorted by the sort keys they were
    // written with, the owner with none last.
    override fun observeOwners(): Flow<Either<PodcastError, List<Owner>>> = read { stored ->
        stored.shows.groupBy { it.write.ownerKey }
            .map { (key, shows) ->
                Owner(key, mostCommonSpelling(shows.map { it.write.ownerName })) to
                    shows.mapNotNull { it.write.ownerSortKey }.minOrNull()
            }
            .sortedWith(
                compareBy<Pair<Owner, String?>> { it.second == null }
                    .thenBy { it.second }
                    .thenBy { it.first.id.value },
            )
            .map { it.first }
    }

    override fun observeShowsByOwner(ownerId: OwnerId): Flow<Either<PodcastError, List<Show>>> = read { stored ->
        stored.shows.filter { it.write.ownerKey == ownerId }
            .sortedWith(compareBy<StoredShow> { it.write.titleSortKey }.thenBy { it.id.value })
            .map { stored.showOf(it) }
    }

    override fun observeShow(showId: ShowId): Flow<Either<PodcastError, Show?>> =
        read { stored -> stored.shows.firstOrNull { it.id == showId }?.let(stored::showOf) }

    override suspend fun getShow(showId: ShowId): Either<PodcastError, Show?> =
        now { stored -> stored.shows.firstOrNull { it.id == showId }?.let(stored::showOf) }

    override fun observeShowByDirectoryId(directoryId: DirectoryId): Flow<Either<PodcastError, Show?>> =
        read { stored -> stored.shows.firstOrNull { it.write.directoryId == directoryId }?.let(stored::showOf) }

    override suspend fun getEpisodeId(directoryId: DirectoryId, itemKey: String): Either<PodcastError, EpisodeId?> =
        now { stored ->
            val show = stored.shows.firstOrNull { it.write.directoryId == directoryId }
            stored.episodes.firstOrNull { it.showId == show?.id && it.write.itemKey == itemKey }?.id
        }

    override fun observeEpisodesByShow(showId: ShowId): Flow<Either<PodcastError, List<EpisodeRecord>>> =
        read { stored ->
            // Newest first, those with no date last, and the last added first among the same date.
            stored.episodes.filter { it.showId == showId }
                .sortedWith(
                    compareBy<StoredEpisode> { it.write.publishedAt == null }
                        .thenByDescending { it.write.publishedAt }
                        .thenByDescending { it.id.value },
                )
                .map(stored::recordOf)
        }

    override fun observeEpisode(episodeId: EpisodeId): Flow<Either<PodcastError, EpisodeRecord?>> =
        read { stored -> stored.episodes.firstOrNull { it.id == episodeId }?.let(stored::recordOf) }

    override suspend fun getEpisode(episodeId: EpisodeId): Either<PodcastError, EpisodeRecord?> =
        now { stored -> stored.episodes.firstOrNull { it.id == episodeId }?.let(stored::recordOf) }

    override suspend fun setFollow(showId: ShowId, followed: Boolean, updatedAt: Instant): Either<PodcastError, Unit> =
        now { stored -> stored.shows.firstOrNull { it.id == showId } }.flatMap { show ->
            if (show == null) return@flatMap PodcastError.ShowNotFound(showId).left()
            storeFollow(show.write.directoryId, show.write.feedUrl, followed, updatedAt)
            Unit.right()
        }

    override suspend fun addEpisode(
        show: ShowWrite,
        episode: EpisodeWrite,
        addedAt: Instant,
    ): Either<PodcastError, EpisodeId> {
        readError?.let { return PodcastError.Storage(it).left() }
        var added = EpisodeId(0)
        state.update { stored ->
            val existingShow = stored.shows.firstOrNull { it.write.directoryId == show.directoryId }
            val storedShow = existingShow ?: StoredShow(ShowId(stored.shows.size + 1L), show)
            val shows = if (existingShow == null) stored.shows + storedShow else stored.shows
            val existingEpisode = stored.episodes.firstOrNull {
                it.showId == storedShow.id && it.write.itemKey == episode.itemKey
            }
            val storedEpisode = existingEpisode ?: StoredEpisode(
                EpisodeId(stored.episodes.size + 1L),
                storedShow.id,
                episode,
            )
            added = storedEpisode.id
            stored.copy(
                shows = shows,
                episodes = if (existingEpisode == null) stored.episodes + storedEpisode else stored.episodes,
            )
        }
        return added.right()
    }

    override suspend fun follow(
        show: ShowWrite,
        episodes: List<EpisodeWrite>,
        followedAt: Instant,
    ): Either<PodcastError, ShowId> {
        readError?.let { return PodcastError.Storage(it).left() }
        var followed = ShowId(0)
        state.update { stored ->
            val existingShow = stored.shows.firstOrNull { it.write.directoryId == show.directoryId }
            val storedShow = existingShow ?: StoredShow(ShowId(stored.shows.size + 1L), show)
            followed = storedShow.id
            var all = stored.episodes
            episodes.forEach { episode ->
                if (all.none { it.showId == storedShow.id && it.write.itemKey == episode.itemKey }) {
                    all = all + StoredEpisode(EpisodeId(all.size + 1L), storedShow.id, episode)
                }
            }
            stored.copy(
                shows = if (existingShow == null) stored.shows + storedShow else stored.shows,
                episodes = all,
                follows = stored.follows + (show.directoryId to (true to followedAt)),
                followFeedUrls = stored.followFeedUrls + (show.directoryId to show.feedUrl),
            )
        }
        return followed.right()
    }

    override suspend fun setProgress(episodeId: EpisodeId, progress: ProgressRecord): Either<PodcastError, Unit> =
        now { stored -> stored.episodes.any { it.id == episodeId } }.flatMap { found ->
            if (!found) return@flatMap PodcastError.EpisodeNotFound(episodeId).left()
            state.update { it.copy(progress = it.progress + (episodeId to progress)) }
            Unit.right()
        }

    private fun <T> read(answer: (Stored) -> T): Flow<Either<PodcastError, T>> =
        combine(state, readErrorState) { stored, error ->
            error?.let { PodcastError.Storage(it).left() } ?: answer(stored).right()
        }

    private fun <T> now(answer: (Stored) -> T): Either<PodcastError, T> =
        readError?.let { PodcastError.Storage(it).left() } ?: answer(state.value).right()

    override suspend fun getArtworkSource(episodeId: EpisodeId): Either<PodcastError, ArtworkSource?> {
        readError?.let { return PodcastError.Storage(it).left() }
        val stored = state.value
        val episode = stored.episodes.firstOrNull { it.id == episodeId }
        val show = stored.shows.firstOrNull { it.id == episode?.showId }
        val source = if (episode != null && show != null) {
            ArtworkSource(
                episodeId = episode.id,
                showId = show.id,
                itemKey = episode.write.itemKey,
                feedUrl = show.write.feedUrl,
                episodeImageUrl = episode.imageUrl,
                showImageUrl = show.imageUrl,
            )
        } else {
            null
        }
        return source.right()
    }

    override suspend fun setImageUrls(
        showId: ShowId,
        showImageUrl: String?,
        episodeImageUrls: Map<String, String>,
    ): Either<PodcastError, Unit> {
        readError?.let { return PodcastError.Storage(it).left() }
        state.update { stored ->
            stored.copy(
                shows = stored.shows.map { if (it.id == showId) it.copy(imageUrl = showImageUrl) else it },
                episodes = stored.episodes.map { episode ->
                    val url = episodeImageUrls[episode.write.itemKey]
                    if (episode.showId == showId && url != null) episode.copy(imageUrl = url) else episode
                },
            )
        }
        return Unit.right()
    }

    override suspend fun getFollowedFeeds(): Either<PodcastError, List<FollowedFeed>> = now { stored ->
        stored.follows.filterValues { it.first }.keys.sortedBy { it.value }.map { directoryId ->
            val show = stored.shows.firstOrNull { it.write.directoryId == directoryId }
            FollowedFeed(
                directoryId = directoryId,
                feedUrl = show?.write?.feedUrl ?: stored.followFeedUrls.getValue(directoryId),
                show = show?.let(stored::showOf),
                etag = show?.etag,
                lastModified = show?.lastModified,
            )
        }
    }

    override suspend fun applyFeed(feed: FeedWrite, readAt: Instant): Either<PodcastError, ShowId?> {
        readError?.let { return PodcastError.Storage(it).left() }
        val directoryId = feed.show.directoryId
        var applied: ShowId? = null
        state.update { stored ->
            if (stored.follows[directoryId]?.first != true) return@update stored
            val existing = stored.shows.firstOrNull { it.write.directoryId == directoryId }
            val show = StoredShow(
                id = existing?.id ?: ShowId(stored.shows.size + 1L),
                write = feed.show,
                imageUrl = feed.imageUrl ?: existing?.imageUrl,
                etag = feed.etag,
                lastModified = feed.lastModified,
            )
            applied = show.id
            val inFeed = feed.episodes.distinctBy { it.itemKey }.associateBy { it.itemKey }
            var episodes = stored.episodes.map { episode ->
                if (episode.showId == show.id) episode.readFrom(inFeed[episode.write.itemKey], readAt) else episode
            }
            inFeed.values.forEach { read ->
                if (episodes.none { it.showId == show.id && it.write.itemKey == read.itemKey }) {
                    episodes = episodes + StoredEpisode(EpisodeId(episodes.size + 1L), show.id, read, read.imageUrl)
                }
            }
            stored.copy(
                shows = if (existing ==
                    null
                ) {
                    stored.shows + show
                } else {
                    stored.shows.map { if (it.id == show.id) show else it }
                },
                episodes = episodes,
                followFeedUrls = stored.followFeedUrls + (directoryId to feed.show.feedUrl),
            )
        }
        return applied.right()
    }

    override suspend fun applyFeedNotModified(
        directoryId: DirectoryId,
        feedUrl: String,
        readAt: Instant,
    ): Either<PodcastError, Unit> {
        readError?.let { return PodcastError.Storage(it).left() }
        state.update { stored ->
            val show = stored.shows.firstOrNull { it.write.directoryId == directoryId }
            if (stored.follows[directoryId]?.first != true || show == null) return@update stored
            stored.copy(
                shows = stored.shows.map { if (it == show) it.copy(write = it.write.copy(feedUrl = feedUrl)) else it },
                followFeedUrls = stored.followFeedUrls + (directoryId to feedUrl),
            )
        }
        return Unit.right()
    }

    /** The episode as a feed read at [readAt] leaves it: updated from [read], or marked as left when it is `null`. */
    private fun StoredEpisode.readFrom(read: EpisodeWrite?, readAt: Instant): StoredEpisode = when {
        read != null -> copy(write = read, imageUrl = read.imageUrl ?: imageUrl, leftFeedAt = null)
        leftFeedAt == null -> copy(leftFeedAt = readAt)
        else -> this
    }

    /** Sets the image URLs the feed would have named, as [setImageUrls] stores them. */
    fun setImages(episodeId: EpisodeId, episodeImageUrl: String?, showImageUrl: String?) {
        state.update { stored ->
            val showId = stored.episodes.first { it.id == episodeId }.showId
            stored.copy(
                shows = stored.shows.map { if (it.id == showId) it.copy(imageUrl = showImageUrl) else it },
                episodes = stored.episodes.map { if (it.id == episodeId) it.copy(imageUrl = episodeImageUrl) else it },
            )
        }
    }

    private data class StoredShow(
        val id: ShowId,
        val write: ShowWrite,
        val imageUrl: String? = null,
        val etag: String? = null,
        val lastModified: String? = null,
    )

    private data class StoredEpisode(
        val id: EpisodeId,
        val showId: ShowId,
        val write: EpisodeWrite,
        val imageUrl: String? = null,
        val leftFeedAt: Instant? = null,
    )

    private data class Stored(
        val shows: List<StoredShow> = emptyList(),
        val episodes: List<StoredEpisode> = emptyList(),
        val progress: Map<EpisodeId, ProgressRecord> = emptyMap(),
        val follows: Map<DirectoryId, Pair<Boolean, Instant>> = emptyMap(),
        val followFeedUrls: Map<DirectoryId, String> = emptyMap(),
    ) {
        fun showOf(show: StoredShow) = Show(
            id = show.id,
            directoryId = show.write.directoryId,
            title = show.write.title,
            ownerId = show.write.ownerKey,
            ownerName = show.write.ownerName,
            isFollowed = follows[show.write.directoryId]?.first == true,
        )

        fun recordOf(episode: StoredEpisode) = EpisodeRecord(
            id = episode.id,
            showId = episode.showId,
            itemKey = episode.write.itemKey,
            title = episode.write.title,
            publishedAt = episode.write.publishedAt,
            feedLength = episode.write.length,
            description = episode.write.description,
            audioUrl = episode.write.audioUrl,
            audioSizeBytes = episode.write.audioSizeBytes,
            progress = progress[episode.id],
            leftFeedAt = episode.leftFeedAt,
        )
    }
}

/** The spelling most have, and the first A to Z among those tied, as the database picks it. */
private fun mostCommonSpelling(names: List<String?>): String? = names.groupingBy { it }.eachCount()
    .entries
    .sortedWith(compareByDescending<Map.Entry<String?, Int>> { it.value }.thenBy(nullsFirst()) { it.key })
    .first()
    .key
