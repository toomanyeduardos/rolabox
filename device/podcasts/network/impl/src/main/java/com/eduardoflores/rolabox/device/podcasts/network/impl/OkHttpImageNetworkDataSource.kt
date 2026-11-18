package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkError
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The most an image may weigh. Feed images are often 3,000 pixels square, a few megabytes as JPEG; anything much larger
 * isn't kept, so a wrong URL can't fill the memory.
 */
internal const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

/** Images over OkHttp, the podcasts' client (ADR-031, Artwork; ADR-027, rule 7). Redirects are followed. */
internal class OkHttpImageNetworkDataSource @Inject constructor(
    @param:PodcastsClient private val client: Lazy<OkHttpClient>,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ImageNetworkDataSource {
    override suspend fun fetch(url: String): Either<ImageNetworkError, ByteArray> = withContext(ioDispatcher) {
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext ImageNetworkError.Unusable.left()
        try {
            client.get().newCall(Request.Builder().url(httpUrl).build()).await().use(::bytesOf)
        } catch (_: IOException) {
            // No network, a connection that failed or a timeout, before the answer or while it is read.
            ImageNetworkError.Unreachable.left()
        }
    }

    private fun bytesOf(response: Response): Either<ImageNetworkError, ByteArray> {
        val length = response.body.contentLength()
        return when {
            !response.isSuccessful -> ImageNetworkError.Unusable.left()

            length > MAX_IMAGE_BYTES -> ImageNetworkError.Unusable.left()

            else -> {
                // A body with no length is read only up to the limit, and one past it isn't kept.
                val bytes = response.body.source().use { source ->
                    source.request(MAX_IMAGE_BYTES + 1)
                    source.buffer.readByteArray(minOf(source.buffer.size, MAX_IMAGE_BYTES + 1))
                }
                if (bytes.size > MAX_IMAGE_BYTES) ImageNetworkError.Unusable.left() else bytes.right()
            }
        }
    }
}
