package com.eduardoflores.rolabox.device.podcasts.network.api

import arrow.core.Either
import java.time.Instant
import kotlin.time.Duration

/** Shows' RSS feeds, read on the phone (ADR-031, Feeds). */
interface FeedNetworkDataSource {
    /**
     * The feed at [url], asking only for what changed since the answer [since] describes. A feed is read whole or not
     * at all: one that isn't RSS, or ends early, is [FeedNetworkError.Unreadable], with nothing of it kept.
     */
    suspend fun fetch(url: String, since: FeedValidators = FeedValidators.None): Either<FeedNetworkError, FeedAnswer>
}

/** The `ETag` and `Last-Modified` of a feed's last answer, sent back so that an unchanged feed answers 304. */
data class FeedValidators(val etag: String?, val lastModified: String?) {
    companion object {
        val None = FeedValidators(etag = null, lastModified = null)
    }
}

/**
 * What a feed answered. [movedTo] is the URL that replaces the feed's stored one, from a permanent redirect (301 or
 * 308) or the feed's `itunes:new-feed-url`, or `null` when it didn't move.
 */
sealed interface FeedAnswer {
    val movedTo: String?

    /** The feed, with the [validators] of this answer, to send back the next time. */
    data class Read(val feed: Feed, val validators: FeedValidators, override val movedTo: String?) : FeedAnswer

    /** The feed hasn't changed since the validators sent: a 304. */
    data class NotModified(override val movedTo: String?) : FeedAnswer
}

/**
 * A show as its feed has it. [ownerName] is `itunes:author`, then `itunes:owner`'s `itunes:name`, or `null`.
 * [imageUrl] is `itunes:image`, then `image/url`. [episodes] are the items with an audio enclosure, newest first, and
 * those with no date last, in the feed's order.
 */
data class Feed(val title: String?, val ownerName: String?, val imageUrl: String?, val episodes: List<FeedEpisode>)

/**
 * An item of a feed with an audio enclosure. [guid] is `null` when the item has none: it is then known by
 * [audioUrl]. [length] is `itunes:duration`, and [description] plain text, from `description`, then `itunes:summary`,
 * then `content:encoded`. [imageUrl] is the item's own `itunes:image`.
 */
data class FeedEpisode(
    val guid: String?,
    val title: String?,
    val publishedAt: Instant?,
    val length: Duration?,
    val description: String?,
    val audioUrl: String,
    val audioSizeBytes: Long?,
    val audioType: String?,
    val imageUrl: String?,
)

/** Why a feed gave no answer (ADR-007). */
sealed interface FeedNetworkError {
    /** No network, a connection that failed, or a timeout. */
    data object Unreachable : FeedNetworkError

    /** An HTTP error, or a body that isn't RSS or ends early. */
    data object Unreadable : FeedNetworkError
}
