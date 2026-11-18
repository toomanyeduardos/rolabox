package com.eduardoflores.rolabox.device.podcasts.local.api

import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import java.time.Instant
import kotlin.time.Duration

/**
 * A show to add. [ownerKey] is the grouping key of [ownerName], and [titleSortKey] and [ownerSortKey] what the title
 * and the owner sort by, all of which the part's `:impl` decides (ADR-031). [ownerSortKey] is `null` with no owner.
 */
data class ShowWrite(
    val directoryId: DirectoryId,
    val feedUrl: String,
    val title: String,
    val ownerName: String?,
    val ownerKey: OwnerId,
    val titleSortKey: String,
    val ownerSortKey: String?,
)

/**
 * An episode to add. [itemKey] is what identifies it within its show: its GUID, or its enclosure URL when it has none
 * (ADR-031, Feeds).
 */
data class EpisodeWrite(
    val itemKey: String,
    val guid: String?,
    val title: String?,
    val publishedAt: Instant?,
    val length: Duration?,
    val description: String?,
    val audioUrl: String,
    val audioSizeBytes: Long? = null,
    val audioType: String? = null,
)

/**
 * Where an episode is. [playerLength] is the length the player knew when it stored the position, which replaces the
 * feed's. [lastPlayedAt] is when the position was stored.
 */
data class ProgressRecord(
    val position: Duration,
    val isPlayed: Boolean,
    val playerLength: Duration?,
    val lastPlayedAt: Instant,
)

/**
 * An episode as stored, with its progress apart from what the feed says (ADR-031, The database): [feedLength] is the
 * feed's, and [progress] is `null` for an episode never started.
 */
data class EpisodeRecord(
    val id: EpisodeId,
    val showId: ShowId,
    val title: String?,
    val publishedAt: Instant?,
    val feedLength: Duration?,
    val description: String?,
    val audioUrl: String,
    val audioSizeBytes: Long?,
    val progress: ProgressRecord?,
)

/**
 * What an episode's artwork is downloaded from (ADR-031, Artwork): its own image and its show's, as the feed named
 * them, `null` when it named none or it wasn't read for them yet, and the show's [feedUrl] to read them from.
 * [itemKey] finds the episode in its feed.
 */
data class ArtworkSource(
    val episodeId: EpisodeId,
    val showId: ShowId,
    val itemKey: String,
    val feedUrl: String,
    val episodeImageUrl: String?,
    val showImageUrl: String?,
)
