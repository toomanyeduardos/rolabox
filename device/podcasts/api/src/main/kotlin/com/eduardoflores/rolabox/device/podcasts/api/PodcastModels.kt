package com.eduardoflores.rolabox.device.podcasts.api

import java.time.Instant
import kotlin.time.Duration

/**
 * An owner is the grouping key of its shows, their owner's name ignoring case and whitespace (ADR-031, The database).
 * The key of shows with no owner is empty.
 */
@JvmInline
value class OwnerId(val value: String)

/** The podcasts database's own id of a show (ADR-031). */
@JvmInline
value class ShowId(val value: Long)

/** The podcasts database's own id of an episode (ADR-031). */
@JvmInline
value class EpisodeId(val value: Long)

/**
 * A show's identity in the directory: its `collectionId` as `itunes-<id>` (ADR-031, The directory). It names the
 * show across phones, which the database's [ShowId] doesn't.
 */
@JvmInline
value class DirectoryId(val value: String) {
    companion object {
        fun itunes(collectionId: Long): DirectoryId = DirectoryId("itunes-$collectionId")
    }
}

/** An owner of shows. [name] is `null` for the shows with no owner, whose words are the screens' (ADR-031). */
data class Owner(val id: OwnerId, val name: String?)

/**
 * A show of the library. [ownerName] is the show's own spelling of its owner, or `null` when it has none. [isFollowed]
 * is its Follow switch, Off for a show added by playing one of its episodes (ADR-031). No model holds an image or an
 * image URL: artwork comes from the podcasts' artwork repository.
 */
data class Show(
    val id: ShowId,
    val directoryId: DirectoryId,
    val title: String,
    val ownerId: OwnerId,
    val ownerName: String?,
    val isFollowed: Boolean,
)

/** Where the user is in an episode (ADR-031, Played, partly played and unplayed). */
enum class PlayedState {
    /** Never started: a full dot. */
    Unplayed,

    /** Started, and not yet played: a half dot. */
    PartlyPlayed,

    /** Its position reached 90% of its length, or it ended. It stays played: no dot. */
    Played,
}

/** Whether an episode's audio is on the phone (ADR-031, Downloads). Failed reads as [NotDownloaded]. */
sealed interface DownloadState {
    data object NotDownloaded : DownloadState

    /** Waiting or running. [fraction] is of the bytes, from 0 to 1. */
    data class Downloading(val fraction: Float) : DownloadState

    data object Downloaded : DownloadState
}

/**
 * An episode of a show. [publishedAt] is the feed's date, and [length] the player's once it has played the episode,
 * else the feed's, else `null`. [position] is where it resumes from, zero when it was never started. [audioUrl] is
 * the enclosure's URL, and [audioSizeBytes] its size as the feed gives it, when it does.
 */
data class Episode(
    val id: EpisodeId,
    val showId: ShowId,
    val title: String?,
    val publishedAt: Instant?,
    val length: Duration?,
    val description: String?,
    val audioUrl: String,
    val audioSizeBytes: Long?,
    val playedState: PlayedState,
    val position: Duration,
    val download: DownloadState,
)

/**
 * A show as the directory answers it (ADR-031, The directory). [creator] is the directory's `artistName`, which is
 * the show's owner until its feed is read. A show with no feed URL isn't one, since it can't be played.
 */
data class DirectoryShow(val directoryId: DirectoryId, val title: String, val creator: String?, val feedUrl: String)

/**
 * An episode as the directory or its show's feed answers it. [guid] is the feed's GUID, when there is one, and
 * [audioUrl] the enclosure's URL. [audioSizeBytes] and [audioType] are the enclosure's, which only the feed gives.
 * The description is plain text.
 */
data class DirectoryEpisode(
    val guid: String?,
    val title: String?,
    val publishedAt: Instant?,
    val length: Duration?,
    val description: String?,
    val audioUrl: String,
    val audioSizeBytes: Long? = null,
    val audioType: String? = null,
)

/** An episode a search found, with the show it is of, which playing it from search adds with it (ADR-031). */
data class FoundEpisode(val show: DirectoryShow, val episode: DirectoryEpisode)

/**
 * The directory's answer to a search (ADR-031, The directory): Shows, matching by name or creator, then Episodes,
 * matching by title or description, each in the directory's order. Both are empty when nothing matches.
 */
data class DirectoryResults(val shows: List<DirectoryShow>, val episodes: List<FoundEpisode>) {
    val isEmpty: Boolean get() = shows.isEmpty() && episodes.isEmpty()

    companion object {
        val None = DirectoryResults(emptyList(), emptyList())
    }
}

/**
 * A show opened from search, as its feed has it (frame 32). [show] is the one opened, with the feed URL the feed moved
 * to, if it did. [ownerName] is the feed's owner, else the directory's creator, else `null`. [episodes] are every
 * episode of the feed with audio, newest first.
 */
data class ShowFeed(val show: DirectoryShow, val ownerName: String?, val episodes: List<DirectoryEpisode>)

/**
 * A show opened from search that is in the library (ADR-031, Search): the library's [show], with its Follow switch, and
 * for each episode of the feed, in the feed's order, the library's episode, or `null` when the library hasn't it.
 */
data class ShowInLibrary(val show: Show, val episodes: List<Episode?>)
