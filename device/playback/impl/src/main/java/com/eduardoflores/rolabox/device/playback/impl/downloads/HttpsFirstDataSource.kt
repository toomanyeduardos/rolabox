package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

private const val HTTP = "http"
private const val HTTPS = "https"

/**
 * Opens an `http://` enclosure on the default port as `https://` first, and as given only when that fails: the
 * connection or the TLS handshake fails, or the answer isn't a success (ADR-034, rule 4). Many podcast hosts serve
 * HTTPS but kept an old URL in the feed. A thread interrupted while opening, a load that was cancelled, isn't retried.
 *
 * It wraps the episodes' HTTP, so the download and the stream both go through it. Only the first request is upgraded:
 * [upstream] follows the redirects as the server sends them. The cache key is the episode's, so the two URLs are one
 * download.
 */
@OptIn(UnstableApi::class)
internal class HttpsFirstDataSource(private val upstream: DataSource) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val secure = dataSpec.uri.httpsOrNull() ?: return upstream.open(dataSpec)
        return try {
            upstream.open(dataSpec.withUri(secure))
        } catch (e: IOException) {
            if (Thread.currentThread().isInterrupted) throw e
            upstream.close()
            upstream.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    private fun Uri.httpsOrNull(): Uri? =
        if (scheme.equals(HTTP, ignoreCase = true) && port == -1) buildUpon().scheme(HTTPS).build() else null

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = HttpsFirstDataSource(upstream.createDataSource())
    }
}
