package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkError
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** How many results each of a search's two requests asks for (ADR-031, The directory). */
private const val LIMIT = 25

private const val HTTP_OK = 200
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/** The address of the directory's search, which tests replace with a local server's. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DirectorySearchUrl

/**
 * Apple's iTunes Search API (ADR-031, rule 1). Each request sends the text, what it searches for, the limit and the
 * phone's country, and nothing about the user or the account. The client is created on the first request, so nothing
 * opens a connection before one (ADR-008, rule 8).
 */
internal class ITunesDirectoryNetworkDataSource @Inject constructor(
    @param:PodcastsClient private val client: Lazy<OkHttpClient>,
    @param:DirectorySearchUrl private val searchUrl: HttpUrl,
    private val country: DeviceCountry,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : DirectoryNetworkDataSource {
    override suspend fun searchShows(text: String): Either<DirectoryNetworkError, List<DirectoryShow>> =
        search(text, entity = "podcast", ::parseShows)

    override suspend fun searchEpisodes(text: String): Either<DirectoryNetworkError, List<FoundEpisode>> =
        search(text, entity = "podcastEpisode", ::parseEpisodes)

    private suspend fun <T> search(
        text: String,
        entity: String,
        parse: (String) -> List<T>?,
    ): Either<DirectoryNetworkError, List<T>> = withContext(ioDispatcher) {
        val url = searchUrl.newBuilder()
            .addQueryParameter("term", text)
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", entity)
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("country", country.code())
            .build()
        try {
            client.get().newCall(Request.Builder().url(url).build()).await().use { response ->
                when (response.code) {
                    HTTP_OK -> parse(response.body.string())?.right() ?: DirectoryNetworkError.Failed.left()
                    HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> DirectoryNetworkError.Busy.left()
                    else -> DirectoryNetworkError.Failed.left()
                }
            }
        } catch (_: IOException) {
            // No network, a connection that failed, or a timeout.
            DirectoryNetworkError.Unreachable.left()
        }
    }
}
