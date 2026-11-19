package com.eduardoflores.rolabox.device.podcasts.impl.artwork

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.impl.itemKey
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkName
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkError
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** How a download of an episode's artwork ends, which the work turns into its result. */
internal enum class ArtworkOutcome {
    /** It is kept, there is none to keep, or downloading isn't allowed now. */
    Done,

    /** The network or the storage failed: the work runs again later, with a backoff. */
    Retry,
}

/**
 * The download of one episode's artwork (ADR-031, Artwork): its own image, else its show's. The feed is read for the
 * image URLs when none is known yet, since the directory doesn't give them. One image is downloaded at a time, and only
 * while the Online Artwork setting allows it, which is asked before each request (ADR-027, rule 3).
 */
internal class PodcastArtworkDownloader @Inject constructor(
    private val podcasts: PodcastsLocalDataSource,
    private val files: PodcastArtworkLocalDataSource,
    private val feeds: FeedNetworkDataSource,
    private val images: ImageNetworkDataSource,
    private val libraryArtwork: ArtworkRepository,
) {
    suspend fun download(episodeId: EpisodeId): ArtworkOutcome = either {
        ensureAllowed()
        val stored = podcasts.getArtworkSource(episodeId).mapLeft { ArtworkOutcome.Retry }.bind()
        var source = ensureNotNull(stored) { ArtworkOutcome.Done }
        if (source.episodeImageUrl == null && source.showImageUrl == null) source = readImageUrls(source)
        for (name in source.names()) {
            // Already kept: nothing to do. An image that can't be read from the phone is downloaded again.
            if (files.load(name).getOrNull() != null) return@either ArtworkOutcome.Done
            if (keep(name)) return@either ArtworkOutcome.Done
        }
        ArtworkOutcome.Done
    }.fold(ifLeft = { it }, ifRight = { it })

    /** Downloads and keeps [name]'s image. `false` when it is no image to keep, and the next one is tried. */
    private suspend fun Raise<ArtworkOutcome>.keep(name: ArtworkName): Boolean {
        ensureAllowed()
        val bytes = when (val fetched = images.fetch(name.url)) {
            is Either.Left -> when (fetched.value) {
                ImageNetworkError.Unreachable -> raise(ArtworkOutcome.Retry)
                ImageNetworkError.Unusable -> return false
            }

            is Either.Right -> fetched.value
        }
        return files.save(name, bytes).mapLeft { ArtworkOutcome.Retry }.bind()
    }

    /** Reads the show's feed for the image URLs it names, keeps them, and gives the source with them. */
    private suspend fun Raise<ArtworkOutcome>.readImageUrls(source: ArtworkSource): ArtworkSource {
        ensureAllowed()
        val answer = feeds.fetch(source.feedUrl).mapLeft { error ->
            when (error) {
                FeedNetworkError.Unreachable -> ArtworkOutcome.Retry

                // A feed that can't be read has no images to give: the show is read again only by the follow check.
                FeedNetworkError.Unreadable -> ArtworkOutcome.Done
            }
        }.bind()
        val feed = (answer as? FeedAnswer.Read)?.feed ?: return source
        val episodeImages = feed.episodes.mapNotNull { episode ->
            episode.imageUrl?.let { episode.itemKey to it }
        }.toMap()
        podcasts.setImageUrls(source.showId, feed.imageUrl, episodeImages).mapLeft { ArtworkOutcome.Retry }.bind()
        return source.copy(episodeImageUrl = episodeImages[source.itemKey], showImageUrl = feed.imageUrl)
    }

    private suspend fun Raise<ArtworkOutcome>.ensureAllowed() {
        val allowed = libraryArtwork.observeDownloadEnabled().first().getOrNull() == true
        ensure(allowed) { ArtworkOutcome.Done }
    }
}

/** The images an episode's artwork may be, in the order they are tried: its own, then its show's (ADR-031). */
internal fun ArtworkSource.names(): List<ArtworkName> = listOfNotNull(
    episodeImageUrl?.let { ArtworkName.OfEpisode(episodeId, it) },
    showImageUrl?.let { ArtworkName.OfShow(showId, it) },
)
