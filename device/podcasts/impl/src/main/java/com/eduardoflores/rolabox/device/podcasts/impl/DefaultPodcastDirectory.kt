package com.eduardoflores.rolabox.device.podcasts.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryResults
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.ConnectionNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** How many searches' answers are kept in memory (ADR-031, The directory). */
internal const val REMEMBERED_SEARCHES = 20

/** How many shows' feeds are kept in memory (ADR-042): fewer than searches, since a feed can be long. */
internal const val REMEMBERED_FEEDS = 5

/** How long a feed kept in memory is used before it is read again (ADR-042), so a new episode shows up soon. */
internal val FEED_MEMORY_TIME = 5.minutes

private val Whitespace = Regex("\\s+")

/**
 * The directory and the feeds of the shows it finds, read from the network and never written to the database
 * (ADR-031, rule 4). The answers to the last [REMEMBERED_SEARCHES] texts are kept, so deleting a letter doesn't ask
 * again. A failed search isn't kept: asking again asks the directory. The feeds of the last [REMEMBERED_FEEDS] shows
 * are kept too (ADR-042), for [FEED_MEMORY_TIME], so an episode opened from a show's screen, or the show's screen
 * coming back, doesn't read the whole feed again. A feed is kept by its URL, however the show was found, and a feed
 * that couldn't be read isn't kept.
 */
@Singleton
internal class DefaultPodcastDirectory @Inject constructor(
    private val directory: DirectoryNetworkDataSource,
    private val feeds: FeedNetworkDataSource,
    private val connection: ConnectionNetworkDataSource,
    private val audio: AudioNetworkDataSource,
    private val timeSource: TimeSource.WithComparableMarks,
) : PodcastDirectory {
    private val remembered = RecentAnswers<String, DirectoryResults>(REMEMBERED_SEARCHES)
    private val rememberedFeeds = RecentAnswers<String, FeedRead>(REMEMBERED_FEEDS)

    override suspend fun search(text: String): Either<PodcastError, DirectoryResults> {
        val query = text.trim().replace(Whitespace, " ")
        if (query.isEmpty()) return DirectoryResults.None.right()
        // The directory ignores case, so "Rome" and "rome" are one search.
        val key = query.lowercase(Locale.ROOT)
        return remembered[key]?.right() ?: coroutineScope {
            val shows = async { directory.searchShows(query) }
            val episodes = async { directory.searchEpisodes(query) }
            either {
                DirectoryResults(
                    shows = shows.await().mapLeft(::errorOf).bind(),
                    episodes = episodes.await().mapLeft(::errorOf).bind(),
                )
            }
        }.onRight { remembered[key] = it }
    }

    override fun observeConnected(): Flow<Boolean> = connection.observeConnected()

    override suspend fun checkAudio(audioUrl: String): Either<PodcastError, Unit> =
        audio.check(audioUrl).mapLeft(::errorOf)

    override suspend fun show(show: DirectoryShow): Either<PodcastError, ShowFeed> {
        val kept = rememberedFeeds[show.feedUrl]?.takeIf { it.readAt.elapsedNow() < FEED_MEMORY_TIME }
        return (kept?.right() ?: read(show.feedUrl).onRight { rememberedFeeds[show.feedUrl] = it })
            .map { it.feedOf(show) }
    }

    private suspend fun read(feedUrl: String): Either<PodcastError, FeedRead> = either {
        when (val answer = feeds.fetch(feedUrl).mapLeft(::errorOf).bind()) {
            // Nothing was sent to compare with, so a 304 is an answer that can't be used.
            is FeedAnswer.NotModified -> raise(PodcastError.FeedUnreadable)

            is FeedAnswer.Read -> FeedRead(
                ownerName = answer.feed.ownerName,
                movedTo = answer.movedTo,
                episodes = answer.feed.episodes.map { it.toDirectoryEpisode() },
                readAt = timeSource.markNow(),
            )
        }
    }
}

/**
 * A feed as it was read, kept by its URL: what doesn't depend on how its show was found. An episode found by search
 * has no creator, and a show found by search has, so the show is added when the feed is given out.
 */
private data class FeedRead(
    val ownerName: String?,
    val movedTo: String?,
    val episodes: List<DirectoryEpisode>,
    val readAt: ComparableTimeMark,
) {
    fun feedOf(show: DirectoryShow) = ShowFeed(
        show = movedTo?.let { show.copy(feedUrl = it) } ?: show,
        // The feed's owner, then the directory's creator (ADR-031, Feeds).
        ownerName = ownerName ?: show.creator,
        episodes = episodes,
    )
}

private fun errorOf(error: DirectoryNetworkError): PodcastError = when (error) {
    DirectoryNetworkError.Unreachable -> PodcastError.NoConnection
    DirectoryNetworkError.Busy -> PodcastError.DirectoryBusy
    DirectoryNetworkError.Failed -> PodcastError.DirectoryFailed
}

private fun errorOf(error: FeedNetworkError): PodcastError = when (error) {
    FeedNetworkError.Unreachable -> PodcastError.NoConnection
    FeedNetworkError.Unreadable -> PodcastError.FeedUnreadable
}

private fun errorOf(error: AudioNetworkError): PodcastError = when (error) {
    AudioNetworkError.Unreachable -> PodcastError.NoConnection
    AudioNetworkError.Refused -> PodcastError.AudioRefused
}

private fun FeedEpisode.toDirectoryEpisode() = DirectoryEpisode(
    guid = guid,
    title = title,
    publishedAt = publishedAt,
    length = length,
    description = description,
    audioUrl = audioUrl,
    audioSizeBytes = audioSizeBytes,
    audioType = audioType,
)
