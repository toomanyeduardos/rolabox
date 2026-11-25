package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.api.ShowInLibrary
import com.eduardoflores.rolabox.device.podcasts.api.ShowKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

/**
 * Podcasts that tests fill in: set [owners], [shows] and [episodes], or start from [PodcastSamples] with
 * [fillWithSamples], and the observed flows follow. The lists are returned as they are set: the fake neither sorts
 * nor filters by Follow. A show from search is the library's when a show has its directory id, and an episode of it
 * when an episode of that show has its enclosure URL. Like any repository, a `Left` ends its flow: a lookup of
 * something that isn't there, or a read error set with [setReadError]. A write error set with [setWriteError] makes
 * the writes fail.
 */
@Suppress("TooManyFunctions") // One for each function of the repository, and the fake's own setters.
@Singleton
class FakePodcastsRepository @Inject constructor() : PodcastsRepository {
    private val ownersState = MutableStateFlow<List<Owner>>(emptyList())
    private val showsState = MutableStateFlow<List<Show>>(emptyList())
    private val episodesState = MutableStateFlow<List<Episode>>(emptyList())
    private val readError = MutableStateFlow<StorageError?>(null)
    private var writeError: StorageError? = null

    /** What [addEpisode] was called with, in order. */
    val added = mutableListOf<Pair<DirectoryShow, DirectoryEpisode>>()

    /** The feed's kind each call of [addEpisode] was given, in order. */
    val addedKinds = mutableListOf<ShowKind?>()

    /** What [follow] was called with, in order. */
    val followed = mutableListOf<ShowFeed>()

    /** The id [addEpisode] returns. */
    var addedEpisodeId = EpisodeId(ADDED_EPISODE_ID)

    var owners: List<Owner>
        get() = ownersState.value
        set(value) {
            ownersState.value = value
        }

    var shows: List<Show>
        get() = showsState.value
        set(value) {
            showsState.value = value
        }

    var episodes: List<Episode>
        get() = episodesState.value
        set(value) {
            episodesState.value = value
        }

    /** The owners, shows and The Daily's episodes of the designs. */
    fun fillWithSamples() {
        owners = PodcastSamples.owners
        shows = PodcastSamples.shows
        episodes = PodcastSamples.theDailyEpisodes
    }

    /** Makes every read emit `Left(Storage(error))`, or stops it with `null`. */
    fun setReadError(error: StorageError?) {
        readError.value = error
    }

    /**
     * Makes [setFollowed], [setEpisodeOrder], [addEpisode] and [follow] return `Left(Storage(error))` and change
     * nothing, or stops it with `null`.
     */
    fun setWriteError(error: StorageError?) {
        writeError = error
    }

    override fun observeOwners(): Flow<Either<PodcastError, List<Owner>>> = read(ownersState) { it.right() }

    override fun observeShowsByOwner(ownerId: OwnerId): Flow<Either<PodcastError, List<Show>>> =
        read(showsState) { all ->
            val shows = all.filter { it.ownerId == ownerId }
            if (shows.isEmpty()) PodcastError.OwnerNotFound(ownerId).left() else shows.right()
        }

    override fun observeShow(showId: ShowId): Flow<Either<PodcastError, Show>> = read(showsState) { all ->
        all.firstOrNull { it.id == showId }?.right() ?: PodcastError.ShowNotFound(showId).left()
    }

    override fun observeEpisodesByShow(showId: ShowId): Flow<Either<PodcastError, List<Episode>>> =
        combine(showsState, episodesState) { shows, episodes -> shows to episodes }.let { state ->
            read(state) { (shows, episodes) ->
                if (shows.none { it.id == showId }) {
                    PodcastError.ShowNotFound(showId).left()
                } else {
                    episodes.filter { it.showId == showId }.right()
                }
            }
        }

    override fun observeEpisode(episodeId: EpisodeId): Flow<Either<PodcastError, Episode>> =
        read(episodesState) { all -> episodeOf(all, episodeId) }

    override suspend fun getEpisode(episodeId: EpisodeId): Either<PodcastError, Episode> =
        readError.value?.let { PodcastError.Storage(it).left() } ?: episodeOf(episodes, episodeId)

    override suspend fun setFollowed(showId: ShowId, followed: Boolean): Either<PodcastError, Unit> = write {
        if (shows.none { it.id == showId }) return@write PodcastError.ShowNotFound(showId).left()
        showsState.update { all -> all.map { if (it.id == showId) it.copy(isFollowed = followed) else it } }
        Unit.right()
    }

    // The order is the show's, as set: the episodes aren't sorted by it, as nothing in this fake is.
    override suspend fun setEpisodeOrder(showId: ShowId, order: EpisodeOrder): Either<PodcastError, Unit> = write {
        if (shows.none { it.id == showId }) return@write PodcastError.ShowNotFound(showId).left()
        showsState.update { all -> all.map { if (it.id == showId) it.copy(episodeOrder = order) else it } }
        Unit.right()
    }

    override suspend fun addEpisode(
        show: DirectoryShow,
        episode: DirectoryEpisode,
        kind: ShowKind?,
    ): Either<PodcastError, EpisodeId> = write {
        added += show to episode
        addedKinds += kind
        addedEpisodeId.right()
    }

    override suspend fun follow(feed: ShowFeed): Either<PodcastError, ShowId> = write {
        followed += feed
        val existing = shows.firstOrNull { it.directoryId == feed.show.directoryId }
        val show = existing?.copy(isFollowed = true) ?: Show(
            id = ShowId(FOLLOWED_SHOW_ID + shows.size),
            directoryId = feed.show.directoryId,
            title = feed.show.title,
            ownerId = OwnerId(feed.ownerName.orEmpty().lowercase()),
            ownerName = feed.ownerName,
            isFollowed = true,
        )
        val known = episodes.filter { it.showId == show.id }.map { it.audioUrl }.toSet()
        val added = feed.episodes.filter { it.audioUrl !in known }.mapIndexed { index, episode ->
            Episode(
                id = EpisodeId(FOLLOWED_EPISODE_ID + episodes.size + index),
                showId = show.id,
                title = episode.title,
                publishedAt = episode.publishedAt,
                length = episode.length,
                description = episode.description,
                audioUrl = episode.audioUrl,
                audioSizeBytes = episode.audioSizeBytes,
                playedState = PlayedState.Unplayed,
                position = Duration.ZERO,
                download = DownloadState.NotDownloaded,
            )
        }
        shows = if (existing == null) shows + show else shows.map { if (it.id == show.id) show else it }
        episodes = episodes + added
        show.id.right()
    }

    override suspend fun findShow(show: DirectoryShow): Either<PodcastError, Show?> =
        readError.value?.let { PodcastError.Storage(it).left() } ?: shows.firstOrNull {
            it.directoryId == show.directoryId
        }.right()

    override suspend fun findEpisode(show: DirectoryShow, episode: DirectoryEpisode): Either<PodcastError, EpisodeId?> =
        findShow(show).map { found ->
            episodes.firstOrNull { it.showId == found?.id && it.audioUrl == episode.audioUrl }?.id
        }

    override fun observeInLibrary(feed: ShowFeed): Flow<Either<PodcastError, ShowInLibrary?>> =
        combine(showsState, episodesState) { shows, episodes -> shows to episodes }.let { state ->
            read(state) { (shows, episodes) ->
                val show = shows.firstOrNull { it.directoryId == feed.show.directoryId }
                show?.let { found ->
                    val byUrl = episodes.filter { it.showId == found.id }.associateBy { it.audioUrl }
                    ShowInLibrary(found, feed.episodes.map { byUrl[it.audioUrl] })
                }.right()
            }
        }

    /** Runs [change], unless a write error is set: then nothing changes, and the error is returned. */
    private inline fun <T> write(change: () -> Either<PodcastError, T>): Either<PodcastError, T> =
        writeError?.let { PodcastError.Storage(it).left() } ?: change()

    private fun episodeOf(all: List<Episode>, episodeId: EpisodeId): Either<PodcastError, Episode> =
        all.firstOrNull { it.id == episodeId }?.right() ?: PodcastError.EpisodeNotFound(episodeId).left()

    private fun <S, T> read(state: Flow<S>, answer: (S) -> Either<PodcastError, T>): Flow<Either<PodcastError, T>> =
        combine(state, readError) { value, error ->
            error?.let { PodcastError.Storage(it).left() } ?: answer(value)
        }.transformWhile {
            emit(it)
            it.isRight()
        }

    private companion object {
        const val ADDED_EPISODE_ID = 100L
        const val FOLLOWED_SHOW_ID = 200L
        const val FOLLOWED_EPISODE_ID = 300L
    }
}
