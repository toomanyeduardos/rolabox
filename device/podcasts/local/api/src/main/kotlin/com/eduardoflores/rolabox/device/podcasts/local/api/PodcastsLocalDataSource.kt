package com.eduardoflores.rolabox.device.podcasts.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The podcasts' database: shows, episodes and follows, in the orders
 * [com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository] promises. A `Left` ends a flow. A show or an
 * episode that isn't there is `null`, which only the repository turns into an error.
 */
@Suppress("TooManyFunctions") // One for each read and write of the repositories it serves.
interface PodcastsLocalDataSource {
    /** Every owner of a show, each with the spelling most of its shows have. */
    fun observeOwners(): Flow<Either<PodcastError, List<Owner>>>

    fun observeShowsByOwner(ownerId: OwnerId): Flow<Either<PodcastError, List<Show>>>

    fun observeShow(showId: ShowId): Flow<Either<PodcastError, Show?>>

    suspend fun getShow(showId: ShowId): Either<PodcastError, Show?>

    /** The show with that directory id, which names it across phones, or `null` when the library hasn't it. */
    fun observeShowByDirectoryId(directoryId: DirectoryId): Flow<Either<PodcastError, Show?>>

    /** The id of the episode with [itemKey] in the show with [directoryId], or `null` when the library hasn't it. */
    suspend fun getEpisodeId(directoryId: DirectoryId, itemKey: String): Either<PodcastError, EpisodeId?>

    /** Every episode stored for the show, newest first, and those with no date last. */
    fun observeEpisodesByShow(showId: ShowId): Flow<Either<PodcastError, List<EpisodeRecord>>>

    fun observeEpisode(episodeId: EpisodeId): Flow<Either<PodcastError, EpisodeRecord?>>

    suspend fun getEpisode(episodeId: EpisodeId): Either<PodcastError, EpisodeRecord?>

    /**
     * Stores the show's Follow switch, by its directory id and with its feed URL, as sync keeps it (ADR-031).
     * Returns [PodcastError.ShowNotFound] when there is no such show.
     */
    suspend fun setFollow(showId: ShowId, followed: Boolean, updatedAt: Instant): Either<PodcastError, Unit>

    /**
     * Adds the show, unless one with its directory id is there, and the episode, unless its show has one with its
     * item key, in one transaction. What is already there is kept as it is. Returns the episode's id.
     */
    suspend fun addEpisode(show: ShowWrite, episode: EpisodeWrite, addedAt: Instant): Either<PodcastError, EpisodeId>

    /**
     * Follows the show, as following it from search does (ADR-031, Search): in one transaction, adds the show unless
     * one with its directory id is there, each of [episodes] its show hasn't by item key, and its follow On at
     * [followedAt]. What is already there is kept as it is. Returns the show's id.
     */
    suspend fun follow(show: ShowWrite, episodes: List<EpisodeWrite>, followedAt: Instant): Either<PodcastError, ShowId>

    /** Where the episode's artwork comes from, or `null` when there is no such episode. */
    suspend fun getArtworkSource(episodeId: EpisodeId): Either<PodcastError, ArtworkSource?>

    /**
     * Stores the image URLs the show's feed names: the show's [showImageUrl], and each of its episodes' by item key, in
     * [episodeImageUrls]. An episode of the show that isn't in the map keeps what it has.
     */
    suspend fun setImageUrls(
        showId: ShowId,
        showImageUrl: String?,
        episodeImageUrls: Map<String, String>,
    ): Either<PodcastError, Unit>

    /** Every show whose follow is On, whether or not the show is in the library yet. */
    suspend fun getFollowedFeeds(): Either<PodcastError, List<FollowedFeed>>

    /**
     * Applies a feed read whole to its show (ADR-031, Follow checks), in one transaction, and only while the show's
     * follow is On: adds the show unless one with its directory id is there, or updates it from [feed]; adds each
     * episode its show hasn't by item key, as listed at [readAt]; updates what the feed says of the others, and clears
     * when they left it; and stores [readAt] as when the episodes the show has and the feed hasn't left it, unless
     * they already had. The follow's feed URL becomes the show's, and its `updatedAt` stays. A show's image URL, and
     * an episode's, is kept when the feed names none. No progress is touched. Returns the show's id, or `null` when
     * its follow isn't On, and nothing was written.
     */
    suspend fun applyFeed(feed: FeedWrite, readAt: Instant): Either<PodcastError, ShowId?>

    /**
     * Records that the feed of the show with [directoryId] answered 304 at [readAt], from [feedUrl], the URL it is now
     * at: the show's and its follow's feed URL become it, and no episode changes. Nothing is written when the show
     * isn't in the library or its follow isn't On.
     */
    suspend fun applyFeedNotModified(
        directoryId: DirectoryId,
        feedUrl: String,
        readAt: Instant,
    ): Either<PodcastError, Unit>
}
