package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Episodes' artwork that tests set in [artwork], which the observed flows follow, as an image that arrives would.
 * [error] makes every call return it.
 */
@Singleton
class FakePodcastArtworkRepository @Inject constructor() : PodcastArtworkRepository {
    private val state = MutableStateFlow<Map<EpisodeId, ArtworkImage>>(emptyMap())

    var artwork: Map<EpisodeId, ArtworkImage>
        get() = state.value
        set(value) {
            state.value = value
        }

    var error: PodcastError? = null

    /** The episodes whose artwork was observed, in order. */
    val observed = mutableListOf<EpisodeId>()

    override suspend fun loadArtwork(episodeId: EpisodeId): Either<PodcastError, ArtworkImage?> =
        error?.left() ?: artwork[episodeId].right()

    override fun observeArtwork(episodeId: EpisodeId): Flow<Either<PodcastError, ArtworkImage?>> {
        observed += episodeId
        return state.map { all -> error?.left() ?: all[episodeId].right() }.distinctUntilChanged()
    }
}
