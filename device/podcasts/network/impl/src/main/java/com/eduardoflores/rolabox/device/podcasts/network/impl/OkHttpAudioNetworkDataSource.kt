package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkError
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** The answers that mean the publisher refuses the audio, as it does a paid episode kept in a public feed. */
private val REFUSALS = setOf(401, 403, 410)

/**
 * The longest the check may take, redirects included. The screen offers nothing until it answers, so a slow server
 * gets Play after this, as if it had answered (ADR-031, Search).
 */
internal val AUDIO_CHECK_TIMEOUT = 10.seconds

/** The audio check over OkHttp, the podcasts' client (ADR-031, Search). Redirects are followed, with the range. */
internal class OkHttpAudioNetworkDataSource @Inject constructor(
    @param:PodcastsClient private val client: Lazy<OkHttpClient>,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AudioNetworkDataSource {
    private val checkClient by lazy {
        client.get().newBuilder().callTimeout(AUDIO_CHECK_TIMEOUT.toJavaDuration()).build()
    }

    override suspend fun check(url: String): Either<AudioNetworkError, Unit> = withContext(ioDispatcher) {
        // A URL that isn't one is the player's to fail on: it isn't a refusal.
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext Unit.right()
        val request = Request.Builder().url(httpUrl).header("Range", "bytes=0-1023").build()
        try {
            // The body is closed unread: only the status matters.
            checkClient.newCall(request).await().use { response ->
                if (response.code in REFUSALS) AudioNetworkError.Refused.left() else Unit.right()
            }
        } catch (_: IOException) {
            // No network, a connection that failed or a timeout.
            AudioNetworkError.Unreachable.left()
        }
    }
}
