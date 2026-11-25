package com.eduardoflores.rolabox.device.podcasts.impl.follow

import android.util.Log
import arrow.core.Either
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.impl.cleanedName
import com.eduardoflores.rolabox.device.podcasts.impl.itemKey
import com.eduardoflores.rolabox.device.podcasts.impl.ownerIdOf
import com.eduardoflores.rolabox.device.podcasts.impl.sortKeyOf
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FeedWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.FollowedFeed
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators
import java.time.Clock
import javax.inject.Inject

/** How a check ends, which the work turns into its result. */
internal enum class FollowCheckOutcome {
    /** Every followed feed was read, or skipped until the next check. */
    Done,

    /** The follows couldn't be read from the database: the work runs again later, with a backoff. */
    Retry,
}

/**
 * The follow check (ADR-031, Follow checks): reads the feed of each followed show, with the validators of its last
 * answer, and applies it to the library. A feed that can't be read, or is read with no episode, is skipped until the
 * next check and logged, and isn't reported to Crashlytics: it is the publisher's failure (ADR-030, rule 6). Nothing
 * is downloaded, and no progress is touched.
 */
internal class FollowCheck @Inject constructor(
    private val local: PodcastsLocalDataSource,
    private val feeds: FeedNetworkDataSource,
    private val clock: Clock,
    private val log: FollowCheckLog,
) {
    /** Reads every followed show's feed, one at a time. */
    suspend fun checkAll(): FollowCheckOutcome = check { true }

    /** Reads only the feed of the show with [directoryId], as turning its Follow On does. */
    suspend fun checkShow(directoryId: DirectoryId): FollowCheckOutcome = check { it.directoryId == directoryId }

    private suspend fun check(which: (FollowedFeed) -> Boolean): FollowCheckOutcome {
        val followed = when (val read = local.getFollowedFeeds()) {
            is Either.Left -> {
                log.failed(null, read.value.toString())
                return FollowCheckOutcome.Retry
            }

            is Either.Right -> read.value
        }
        followed.filter(which).forEach { read(it) }
        return FollowCheckOutcome.Done
    }

    private suspend fun read(followed: FollowedFeed) {
        val id = followed.directoryId
        // A show not in the library yet has no validators: its feed is read whole.
        val since = FeedValidators(followed.etag, followed.lastModified)
        val answer = when (val fetched = feeds.fetch(followed.feedUrl, since)) {
            is Either.Left -> return log.failed(id, fetched.value.toString())
            is Either.Right -> fetched.value
        }
        val feedUrl = answer.movedTo ?: followed.feedUrl
        val applied = when (answer) {
            is FeedAnswer.NotModified -> local.applyFeedNotModified(id, feedUrl, clock.instant())

            is FeedAnswer.Read -> {
                val write = answer.toWrite(followed, feedUrl) ?: return
                local.applyFeed(write, clock.instant())
            }
        }
        // The database reports its own failures (ADR-031, Errors). The next check reads the feed again.
        applied.onLeft { log.failed(id, it.toString()) }
    }

    /** The feed as the library keeps it, or `null`, logged, when it can't be applied. */
    private fun FeedAnswer.Read.toWrite(followed: FollowedFeed, feedUrl: String): FeedWrite? {
        // A show is never stored without a title, and a follow from sync carries none of its own.
        val title = cleanedName(feed.title) ?: followed.show?.title
        // A feed with no episode would hide every episode of the show without a download: a broken feed is likelier.
        val unusable = if (feed.episodes.isEmpty()) "NoEpisodes" else "NoTitle".takeIf { title == null }
        if (unusable != null) log.failed(followed.directoryId, unusable)
        if (unusable != null || title == null) return null
        // The feed's owner, then the one the library has, which may be the directory's creator (ADR-031, Feeds).
        val owner = cleanedName(feed.ownerName) ?: followed.show?.ownerName
        return FeedWrite(
            show = ShowWrite(
                directoryId = followed.directoryId,
                feedUrl = feedUrl,
                title = title,
                ownerName = owner,
                ownerKey = ownerIdOf(owner),
                titleSortKey = sortKeyOf(title),
                ownerSortKey = owner?.let(::sortKeyOf),
                kind = feed.kind,
            ),
            imageUrl = feed.imageUrl,
            etag = validators.etag,
            lastModified = validators.lastModified,
            episodes = feed.episodes.map { it.toWrite() },
        )
    }
}

private fun FeedEpisode.toWrite() = EpisodeWrite(
    itemKey = itemKey,
    guid = guid,
    title = title,
    publishedAt = publishedAt,
    length = length,
    description = description,
    audioUrl = audioUrl,
    audioSizeBytes = audioSizeBytes,
    audioType = audioType,
    imageUrl = imageUrl,
)

/** Where the check says why it skipped a feed: logcat in the app, a list in tests. */
internal interface FollowCheckLog {
    /** The feed of [directoryId], or the follows when it is `null`, couldn't be read or applied, for [reason]. */
    fun failed(directoryId: DirectoryId?, reason: String)
}

/** One `Log.w` line per feed skipped, in every build type, with the show's directory id and the error's type. */
internal class LogcatFollowCheckLog @Inject constructor() : FollowCheckLog {
    override fun failed(directoryId: DirectoryId?, reason: String) {
        Log.w(TAG, "Skipped ${directoryId?.value ?: "the follows"}: $reason")
    }

    private companion object {
        const val TAG = "PodcastFollowCheck"
    }
}
