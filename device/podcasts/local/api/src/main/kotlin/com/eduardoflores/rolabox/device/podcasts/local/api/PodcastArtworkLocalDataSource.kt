package com.eduardoflores.rolabox.device.podcasts.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import kotlinx.coroutines.flow.Flow

/**
 * The episodes' and shows' artwork, as files in the app's files, decoded down to [ARTWORK_SIZE] once when they are
 * kept, since feed images are often 3,000 pixels square and only one size is ever drawn (ADR-031, Artwork). A file is
 * named for its episode or show and its image's URL, so an image that changes in the feed is a new file. There is no
 * table.
 */
interface PodcastArtworkLocalDataSource {
    /** The image kept as [name], or `null` when there is none. */
    suspend fun load(name: ArtworkName): Either<PodcastError, ArtworkImage?>

    /** Each time an image is kept, so what was `null` can be loaded again. */
    fun observeSaved(): Flow<Unit>

    /**
     * Decodes [bytes] down to at most [ARTWORK_SIZE] pixels on the longest side and keeps it as [name]. Returns `false`
     * when the bytes aren't an image, and keeps nothing then.
     */
    suspend fun save(name: ArtworkName, bytes: ByteArray): Either<PodcastError, Boolean>

    companion object {
        /** Now Playing's cover at its largest, as the library's covers are (ADR-026). */
        const val ARTWORK_SIZE = 720
    }
}

/** What an artwork file is named for: an episode's own image, or a show's, each with the URL it was downloaded from. */
sealed interface ArtworkName {
    val url: String

    data class OfEpisode(val episodeId: EpisodeId, override val url: String) : ArtworkName

    data class OfShow(val showId: ShowId, override val url: String) : ArtworkName
}
