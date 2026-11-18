package com.eduardoflores.rolabox.device.playback.impl

import androidx.media3.common.C
import androidx.media3.common.Player
import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * What playback reads and writes of the podcasts (ADR-031, The layout): the episodes and their shows, which make an
 * episode's item, their positions, and their artwork.
 */
internal class Podcasts @Inject constructor(
    val repository: PodcastsRepository,
    val progress: EpisodeProgressRepository,
    val artwork: PodcastArtworkRepository,
)

/** Where [item] resumes from: an episode's position, kept by the podcasts (ADR-031, rule 7), and 0:00 for a song. */
internal suspend fun Podcasts.storedPositionOf(item: PlaybackItemId): Either<PlaybackError, Duration> = when (item) {
    is PlaybackItemId.Song -> Duration.ZERO.right()
    is PlaybackItemId.Episode -> progress.getPosition(item.id).mapLeft(PlaybackError::Podcasts)
}

/** Each change of [item]'s artwork: its song's cover, or its episode's artwork (ADR-031). */
internal fun artworkChangesOf(item: PlaybackItem, artwork: ArtworkRepository, podcasts: Podcasts): Flow<*> =
    when (val id = item.id) {
        is PlaybackItemId.Song -> item.artwork?.let(artwork::observeArtwork) ?: emptyFlow<Unit>()
        is PlaybackItemId.Episode -> podcasts.artwork.observeArtwork(id.id)
    }

/** The length of the item playing as the player knows it, or `null` before it does. */
internal fun Player.length(): Duration? = duration.takeIf { it != C.TIME_UNSET && it > 0 }?.milliseconds
