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
private val ReleasesUrl = "https://musicbrainz.org/ws/2/release".toHttpUrl()
private const val COVER_ART_ARCHIVE = "https://coverartarchive.org/release-group"

/** How many release groups a search returns: more than any album's editions with one first part and artist. */
private const val SEARCH_LIMIT = 10

/** How many releases of a release group are read for their track counts. */
private const val RELEASES_LIMIT = 25

/** The largest image kept. The archive's 500-pixel images are a few dozen kilobytes. */
private const val MAX_COVER_BYTES = 4 * 1024 * 1024

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404

/** The HTTP client of the cover source, with its `User-Agent` (ADR-027). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CoverClient

/**
 * Covers from the Cover Art Archive, matched through a search of MusicBrainz's release groups (ADR-027): pass 1's
 * equal one, or else pass 2's closest one that has a cover. Each request waits its turn in [spacing]. The client is
 * created on the first request, so nothing opens a connection before one (ADR-008, rule 8).
 */
@Singleton
internal class MusicBrainzCoverNetworkDataSource @Inject constructor(
    @param:CoverClient private val client: Lazy<OkHttpClient>,
    private val spacing: RequestSpacing,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoverNetworkDataSource {
    override suspend fun findCover(album: CoverQuery): Either<CoverSourceError, Cover?> = either {
        val first = search(searchQueryOf(album)).bind()
        // Pass 1: one equal is the album, with its cover or none.
        equalsOf(first, album).singleOrNull()?.let { return@either coverOf(it).bind() }
        // Pass 2, searched again with the first part of the title unless that is what pass 1 sent.
        val firstPart = firstPartOf(album.title)
        val second = if (firstPart == withoutStoreNotes(album.title)) {
            emptyList()
        } else {
            search(searchQueryOf(album, firstPart)).bind()
        }
        val candidates = candidatesOf(first + second, album)
        val trackCounts = needingTrackCounts(candidates, album).associate { it.id to trackCountsOf(it).bind() }
        ranked(candidates, album, trackCounts).take(MAX_CANDIDATES).firstNotNullOfOrNull { coverOf(it).bind() }
    }

    private suspend fun search(query: String): Either<CoverSourceError, List<ReleaseGroup>> = either {
        val answer = get(SearchUrl.json(query, SEARCH_LIMIT)).bind() ?: raise(CoverSourceError.Server(HTTP_NOT_FOUND))
        parseReleaseGroups(answer.bytes.decodeToString()) ?: raise(CoverSourceError.Malformed)
    }

    // A release group with no releases has no track counts, and is never enough.
    private suspend fun trackCountsOf(group: ReleaseGroup): Either<CoverSourceError, List<Int>> = either {
        val answer = get(ReleasesUrl.json(releasesQueryOf(group), RELEASES_LIMIT)).bind() ?: return@either emptyList()
        parseTrackCounts(answer.bytes.decodeToString()) ?: raise(CoverSourceError.Malformed)
    }

    // The archive answers 404 when the release group has no front image: no cover from it.
    private suspend fun coverOf(group: ReleaseGroup): Either<CoverSourceError, Cover?> = either {
        val image = get("$COVER_ART_ARCHIVE/${group.id}/front-500".toHttpUrl()).bind() ?: return@either null
        if (!image.isImage || image.bytes.size > MAX_COVER_BYTES) raise(CoverSourceError.Malformed)
        Cover(group.id, image.bytes)
    }

    private fun HttpUrl.json(query: String, limit: Int): HttpUrl = newBuilder()
        .addQueryParameter("query", query)
        .addQueryParameter("fmt", "json")
        .addQueryParameter("limit", limit.toString())
        .build()

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
