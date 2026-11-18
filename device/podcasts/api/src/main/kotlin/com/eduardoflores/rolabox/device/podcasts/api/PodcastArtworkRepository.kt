package com.eduardoflores.rolabox.device.podcasts.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import kotlinx.coroutines.flow.Flow

/**
 * The artwork of the episodes, as pixels (ADR-031, Artwork, rule 12): the episode's own image from its feed, else its
 * show's, else none. The images are downloaded by the podcasts part, decoded down to Now Playing's size, and kept on
 * the phone. No podcasts model holds an image or its URL.
 */
interface PodcastArtworkRepository {
    /**
     * The episode's artwork, as it is now. `null` when there is none yet, which is a valid case: it may not be
     * downloaded, or the feed may have no image. A `Left` is [PodcastError.EpisodeNotFound] or [PodcastError.Storage].
     */
    suspend fun loadArtwork(episodeId: EpisodeId): Either<PodcastError, ArtworkImage?>

    /**
     * The episode's artwork, as [loadArtwork] gives it, and again whenever it changes. What is observed is what is
     * shown, so an episode observed with no artwork has it downloaded, unless the Online Artwork setting is off. A
     * `Left` ends the flow.
     */
    fun observeArtwork(episodeId: EpisodeId): Flow<Either<PodcastError, ArtworkImage?>>
}
