package com.eduardoflores.rolabox.device.podcasts.network.impl

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

private const val HTTP_PORT = 80
private const val HTTP_NOT_MODIFIED = 304

/**
 * Asks for an `http://` URL on the default port as `https://` first, and as given only when that fails to connect or
 * answers with neither a 2xx nor 304 (ADR-034, rule 4). Feeds and their images come from hosts Rolabox doesn't
 * choose, many of which serve HTTPS but kept an old URL in the feed.
 *
 * It is an application interceptor, so only the first request is upgraded: redirects are followed as the server sends
 * them. A cancelled call isn't retried. The URL the caller has, and stores, doesn't change.
 */
internal class HttpsFirstInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val secure = request.url.httpsOrNull()?.let { secureUrl ->
            try {
                chain.proceed(request.newBuilder().url(secureUrl).build())
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                // The connection or the TLS handshake failed: the host may not serve HTTPS.
                null
            }
        }
        return if (secure != null && (secure.isSuccessful || secure.code == HTTP_NOT_MODIFIED)) {
            secure
        } else {
            // Another site may answer on HTTPS, and its 404 says nothing about the feed.
            secure?.close()
            chain.proceed(request)
        }
    }

    private fun HttpUrl.httpsOrNull(): HttpUrl? =
        if (scheme == "http" && port == HTTP_PORT) newBuilder().scheme("https").build() else null
}
