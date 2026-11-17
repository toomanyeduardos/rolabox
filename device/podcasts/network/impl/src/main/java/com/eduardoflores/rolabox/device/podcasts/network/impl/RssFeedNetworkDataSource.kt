package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val HTTP_OK = 200
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_MOVED_PERMANENTLY = 301
private const val HTTP_PERMANENT_REDIRECT = 308

/**
 * Feeds over OkHttp, read with the platform's pull parser as they stream in (ADR-031, Feeds). Redirects are followed,
 * and the first of them that are permanent move the feed. The validators of the last answer are sent back, and a 304
 * is "nothing new".
 */
internal class RssFeedNetworkDataSource @Inject constructor(
    @param:PodcastsClient private val client: Lazy<OkHttpClient>,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : FeedNetworkDataSource {
    override suspend fun fetch(url: String, since: FeedValidators): Either<FeedNetworkError, FeedAnswer> =
        withContext(ioDispatcher) {
            val httpUrl = url.toHttpUrlOrNull() ?: return@withContext FeedNetworkError.Unreadable.left()
            val request = Request.Builder().url(httpUrl).apply {
                since.etag?.let { header("If-None-Match", it) }
                since.lastModified?.let { header("If-Modified-Since", it) }
            }.build()
            try {
                client.get().newCall(request).await().use { response -> answerOf(response) }
            } catch (_: UnreadableFeedException) {
                FeedNetworkError.Unreadable.left()
            } catch (_: IOException) {
                // No network, a connection that failed or a timeout, before the answer or while it is read.
                FeedNetworkError.Unreachable.left()
            }
        }

    private fun answerOf(response: Response): Either<FeedNetworkError, FeedAnswer> {
        val redirectedTo = response.permanentlyMovedTo()
        return when (response.code) {
            HTTP_NOT_MODIFIED -> FeedAnswer.NotModified(redirectedTo).right()

            HTTP_OK -> {
                val parsed = parseFeed(response.body.byteStream())
                val validators = FeedValidators(response.header("ETag"), response.header("Last-Modified"))
                // The feed's own word that it moved is newer than the server's.
                FeedAnswer.Read(parsed.feed, validators, movedTo = parsed.newFeedUrl ?: redirectedTo).right()
            }

            else -> FeedNetworkError.Unreadable.left()
        }
    }
}

/**
 * Where the feed moved, by the redirects that are permanent, before the first that isn't: a temporary redirect after
 * them says nothing about the feed. `null` when the first redirect isn't permanent, or there is none.
 */
private fun Response.permanentlyMovedTo(): String? {
    val hops = generateSequence(this) { it.priorResponse }.toList().asReversed()
    return hops.zipWithNext()
        .takeWhile { (redirect, _) ->
            redirect.code == HTTP_MOVED_PERMANENTLY ||
                redirect.code == HTTP_PERMANENT_REDIRECT
        }
        .lastOrNull()
        ?.let { (_, next) -> next.request.url.toString() }
}
