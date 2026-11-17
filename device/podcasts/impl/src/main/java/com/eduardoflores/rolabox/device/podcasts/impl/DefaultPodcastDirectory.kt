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
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** How many searches' answers are kept in memory (ADR-031, The directory). */
internal const val REMEMBERED_SEARCHES = 20

private val Whitespace = Regex("\\s+")

/**
 * The directory and the feeds of the shows it finds, read from the network and never written to the database
 * (ADR-031, rule 4). The answers to the last [REMEMBERED_SEARCHES] texts are kept, so deleting a letter doesn't ask
 * again. A failed search isn't kept: asking again asks the directory.
 */
@Singleton
internal class DefaultPodcastDirectory @Inject constructor(
    private val directory: DirectoryNetworkDataSource,
    private val feeds: FeedNetworkDataSource,
) : PodcastDirectory {
    private val remembered = RecentAnswers<String, DirectoryResults>(REMEMBERED_SEARCHES)

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

    override suspend fun show(show: DirectoryShow): Either<PodcastError, ShowFeed> = either {
        when (val answer = feeds.fetch(show.feedUrl).mapLeft(::errorOf).bind()) {
            // Nothing was sent to compare with, so a 304 is an answer that can't be used.
            is FeedAnswer.NotModified -> raise(PodcastError.FeedUnreadable)

            is FeedAnswer.Read -> ShowFeed(
                show = answer.movedTo?.let { show.copy(feedUrl = it) } ?: show,
                // The feed's owner, then the directory's creator (ADR-031, Feeds).
                ownerName = answer.feed.ownerName ?: show.creator,
                episodes = answer.feed.episodes.map { it.toDirectoryEpisode() },
            )
        }
    }
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
