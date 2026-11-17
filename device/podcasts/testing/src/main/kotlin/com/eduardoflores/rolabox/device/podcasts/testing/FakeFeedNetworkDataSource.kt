package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators

/**
 * Feeds that tests fill in: the [answers] of each URL, and [FeedNetworkError.Unreadable] for one with none. Every
 * fetch is recorded in [fetched], with the validators it sent.
 */
class FakeFeedNetworkDataSource : FeedNetworkDataSource {
    var answers: Map<String, Either<FeedNetworkError, FeedAnswer>> = emptyMap()

    val fetched = mutableListOf<Pair<String, FeedValidators>>()

    override suspend fun fetch(url: String, since: FeedValidators): Either<FeedNetworkError, FeedAnswer> {
        fetched += url to since
        return answers[url] ?: FeedNetworkError.Unreadable.left()
    }

    /** Sets [answer] as the feed at [url]. */
    fun serve(url: String, answer: FeedAnswer) {
        answers = answers + (url to answer.right())
    }
}
