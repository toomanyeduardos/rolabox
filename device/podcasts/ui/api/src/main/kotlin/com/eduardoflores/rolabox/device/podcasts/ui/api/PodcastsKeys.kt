package com.eduardoflores.rolabox.device.podcasts.ui.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The keys of the podcasts screens on the screen stack inside the display (ADR-018, ADR-031). They carry the ids of
// what they show, not models (ADR-012, rule 2).

/** Podcasts: Search, then the owners A to Z (frames 22 and 23). */
@Serializable
data object PodcastsKey : NavKey

/** An owner's shows, A to Z (frame 24). [ownerId] is the owner's grouping key, empty for the shows with no owner. */
@Serializable
data class PodcastOwnerKey(val ownerId: String) : NavKey

/** A show of the library: Follow, then its episodes newest first (frame 25). */
@Serializable
data class PodcastShowKey(val showId: Long) : NavKey

/** An episode of the library (frame 26). Its entry comes with the Episode screen, in podcasts/06. */
@Serializable
data class PodcastEpisodeKey(val episodeId: Long) : NavKey

/** Search of the directory (frames 29 to 33). Its entry comes with Search, in podcasts/08. */
@Serializable
data object PodcastSearchKey : NavKey
