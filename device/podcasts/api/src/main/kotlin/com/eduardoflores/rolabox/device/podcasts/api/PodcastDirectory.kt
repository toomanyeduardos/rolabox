package com.eduardoflores.rolabox.device.podcasts.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * The podcast directory and the feeds of the shows it finds, read from the network on each call (ADR-031, rule 4).
 * It isn't a repository: its answers are never kept in the database as a cache. A show joins the library only when
 * one of its episodes is played or it is followed, through [PodcastsRepository].
 *
 * No connection is [PodcastError.NoConnection]. Nothing is sent about the user or the account, so it works without
 * one.
 */
interface PodcastDirectory {
    /**
     * Shows and episodes matching [text], from two requests to the directory made at once. No results is
     * [DirectoryResults.None], and so is a blank [text], which isn't sent.
     *
     * The caller sends a search once typing has paused for 400 ms and the text has at least 2 characters, and cancels
     * the search of an older text when a newer one is sent (ADR-031, rule 1). The answers to the last 20 texts are
     * kept in memory, so asking again for one of them makes no request. A refusal for the rate of requests is
     * [PodcastError.DirectoryBusy], and any other answer that can't be used [PodcastError.DirectoryFailed].
     */
    suspend fun search(text: String): Either<PodcastError, DirectoryResults>

    /**
     * The [show], with its owner and every episode of its feed, newest first. A feed that can't be fetched, isn't
     * RSS or ends early is [PodcastError.FeedUnreadable].
     */
    suspend fun show(show: DirectoryShow): Either<PodcastError, ShowFeed>

    /**
     * Whether the publisher gives the audio at [audioUrl], an episode's enclosure, from a request for its first
     * kilobyte, before an episode from search is offered for play (ADR-031, Search). A refusal is
     * [PodcastError.AudioRefused], and no connection [PodcastError.NoConnection]. Any other answer is `Unit`: the
     * player says what it finds.
     */
    suspend fun checkAudio(audioUrl: String): Either<PodcastError, Unit>

    /**
     * Whether the phone has a network that reaches the internet: the state now, and each change after it. Search asks
     * again when it turns `true` after [PodcastError.NoConnection] (ADR-031, Search).
     */
    fun observeConnected(): Flow<Boolean>
}
