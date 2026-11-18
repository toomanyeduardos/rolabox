package com.eduardoflores.rolabox.device.podcasts.impl.artwork

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * The episodes' artwork from the files the download keeps (ADR-031, Artwork): the episode's own image, else its show's,
 * else none. An episode observed with none has its download enqueued, once for each start of the process, while the
 * Online Artwork setting allows it: what is observed is what is shown.
 */
@Singleton
internal class DefaultPodcastArtworkRepository @Inject constructor(
    private val podcasts: PodcastsLocalDataSource,
    private val files: PodcastArtworkLocalDataSource,
    private val scheduler: PodcastArtworkScheduler,
    private val libraryArtwork: ArtworkRepository,
) : PodcastArtworkRepository {
    private val enqueued = ConcurrentHashMap.newKeySet<EpisodeId>()

    override suspend fun loadArtwork(episodeId: EpisodeId): Either<PodcastError, ArtworkImage?> = either {
        val source = podcasts.getArtworkSource(episodeId).bind() ?: raise(PodcastError.EpisodeNotFound(episodeId))
        source.names().firstNotNullOfOrNull { files.load(it).bind() }
    }

    override fun observeArtwork(episodeId: EpisodeId): Flow<Either<PodcastError, ArtworkImage?>> = files.observeSaved()
        .onStart { emit(Unit) }
        .map { loadArtwork(episodeId) }
        .onEach { loaded -> if (loaded.getOrNull() == null && loaded.isRight()) downloadOnce(episodeId) }
        .distinctUntilChanged()

    private suspend fun downloadOnce(episodeId: EpisodeId) {
        val allowed = libraryArtwork.observeDownloadEnabled().first().getOrNull() == true
        if (allowed && enqueued.add(episodeId)) scheduler.enqueue(episodeId)
    }
}
