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

/** An episode of the library: Play, or Resume and Play from Start, then its date, length and description (frame 26). */
@Serializable
data class PodcastEpisodeKey(val episodeId: Long) : NavKey

/** Search of the directory (frames 29 to 34). */
@Serializable
data object PodcastSearchKey : NavKey

/**
 * A show opened from search (frame 32), read from its feed (ADR-031, Search). It isn't in the library, or is with
 * Follow Off, so it has no id of the library's: it carries the show's identity in the directory, [directoryId], and
 * where its feed is, [feedUrl], with the [title] the header shows and the [creator] the directory gave, until the feed
 * is read.
 */
@Serializable
data class PodcastSearchShowKey(
    val directoryId: String,
    val title: String,
    val creator: String?,
    val feedUrl: String,
) : NavKey

/**
 * An episode found by search, or of a show opened from search, that isn't in the library: its Episode screen, with
 * Play (frame 26). It carries its [show] and its identity in the show's feed, its [guid] and enclosure [audioUrl], and
 * the screen reads the rest from the feed (ADR-031, Search).
 */
@Serializable
data class PodcastSearchEpisodeKey(val show: PodcastSearchShowKey, val guid: String?, val audioUrl: String) : NavKey
