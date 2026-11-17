package com.eduardoflores.rolabox.device.podcasts.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * The podcasts in the library: owners, their shows and the shows' episodes, read from the podcasts' own database
 * (ADR-031, rule 4).
 *
 * Owners and shows are sorted A to Z without a leading "The", ignoring case, and the owner with no name last. A
 * show's episodes are newest first. Any read can fail, and a `Left` ends the flow.
 */
interface PodcastsRepository {
    fun observeOwners(): Flow<Either<PodcastError, List<Owner>>>

    /** Emits [PodcastError.OwnerNotFound] and ends when no show has that owner. */
    fun observeShowsByOwner(ownerId: OwnerId): Flow<Either<PodcastError, List<Show>>>

    /** Emits [PodcastError.ShowNotFound] and ends when there is no such show. */
    fun observeShow(showId: ShowId): Flow<Either<PodcastError, Show>>

    /**
     * The episodes the show lists, newest first: with Follow On, every episode its feed has listed since it was
     * followed or added. With Follow Off, only the episodes with a download, done or in progress (ADR-031, The
     * database). Emits [PodcastError.ShowNotFound] and ends when there is no such show.
     */
    fun observeEpisodesByShow(showId: ShowId): Flow<Either<PodcastError, List<Episode>>>

    /** Emits [PodcastError.EpisodeNotFound] and ends when there is no such episode. */
    fun observeEpisode(episodeId: EpisodeId): Flow<Either<PodcastError, Episode>>

    suspend fun getEpisode(episodeId: EpisodeId): Either<PodcastError, Episode>

    /**
     * Turns the show's Follow On or Off. The switch is kept even when Off, as the record that wins over an older On.
     */
    suspend fun setFollowed(showId: ShowId, followed: Boolean): Either<PodcastError, Unit>

    /**
     * Adds the show, with Follow Off, and that one of its episodes, as playing from search does (ADR-031). A show or
     * an episode already in the library is kept as it is. Returns the id of the episode, to play it.
     */
    suspend fun addEpisode(show: DirectoryShow, episode: DirectoryEpisode): Either<PodcastError, EpisodeId>
}
