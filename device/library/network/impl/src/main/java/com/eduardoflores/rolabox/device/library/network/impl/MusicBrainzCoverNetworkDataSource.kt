package com.eduardoflores.rolabox.device.library.network.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.library.network.api.Cover
import com.eduardoflores.rolabox.device.library.network.api.CoverNetworkDataSource
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import com.eduardoflores.rolabox.device.library.network.api.CoverSourceError
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val SearchUrl = "https://musicbrainz.org/ws/2/release-group".toHttpUrl()
private const val COVER_ART_ARCHIVE = "https://coverartarchive.org/release-group"

/** How many release groups a search returns. An album with more than one that matches gets no cover anyway. */
private const val SEARCH_LIMIT = 10

/** The largest image kept. The archive's 500-pixel images are a few dozen kilobytes. */
private const val MAX_COVER_BYTES = 4 * 1024 * 1024

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404

/** The HTTP client of the cover source, with its `User-Agent` (ADR-027). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CoverClient

/**
 * Covers from the Cover Art Archive, matched through a search of MusicBrainz's release groups (ADR-027). Each request
 * waits its turn in [spacing]. The client is created on the first request, so nothing opens a connection before one
 * (ADR-008, rule 8).
 */
@Singleton
internal class MusicBrainzCoverNetworkDataSource @Inject constructor(
    @param:CoverClient private val client: Lazy<OkHttpClient>,
    private val spacing: RequestSpacing,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoverNetworkDataSource {
    override suspend fun findCover(album: CoverQuery): Either<CoverSourceError, Cover?> = either {
        val searchUrl = SearchUrl.newBuilder()
            .addQueryParameter("query", searchQueryOf(album))
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .build()
        val answer = get(searchUrl).bind() ?: raise(CoverSourceError.Server(HTTP_NOT_FOUND))
        val groups = parseReleaseGroups(answer.bytes.decodeToString()) ?: raise(CoverSourceError.Malformed)
        val match = matchOf(groups, album) ?: return@either null
        // The archive answers 404 when the release group has no front image: that album has no cover.
        val image = get("$COVER_ART_ARCHIVE/${match.id}/front-500".toHttpUrl()).bind() ?: return@either null
        if (!image.isImage || image.bytes.size > MAX_COVER_BYTES) raise(CoverSourceError.Malformed)
        Cover(match.id, image.bytes)
    }

    /** The body of [url], or `null` for a 404. Redirects are followed, as the archive's are. */
    private suspend fun get(url: HttpUrl): Either<CoverSourceError, Body?> = spacing.next {
        withContext(ioDispatcher) {
            try {
                client.get().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    when (response.code) {
                        HTTP_OK -> Body(response.body.bytes(), response.header("Content-Type").orEmpty()).right()
                        HTTP_NOT_FOUND -> null.right()
                        else -> CoverSourceError.Server(response.code).left()
                    }
                }
            } catch (_: IOException) {
                // No network, a connection that failed, or a timeout.
                CoverSourceError.Unreachable.left()
            }
        }
    }

    private class Body(val bytes: ByteArray, val contentType: String) {
        val isImage: Boolean get() = contentType.startsWith("image/")
    }
}
