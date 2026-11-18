package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkError

/**
 * Images that tests fill in: the [answers] of each URL, and [ImageNetworkError.Unusable] for one with none. Every
 * fetch is recorded in [fetched].
 */
class FakeImageNetworkDataSource : ImageNetworkDataSource {
    var answers: Map<String, Either<ImageNetworkError, ByteArray>> = emptyMap()

    val fetched = mutableListOf<String>()

    override suspend fun fetch(url: String): Either<ImageNetworkError, ByteArray> {
        fetched += url
        return answers[url] ?: ImageNetworkError.Unusable.left()
    }

    /** Sets [bytes] as the image at [url]. */
    fun serve(url: String, bytes: ByteArray) {
        answers = answers + (url to bytes.right())
    }
}
