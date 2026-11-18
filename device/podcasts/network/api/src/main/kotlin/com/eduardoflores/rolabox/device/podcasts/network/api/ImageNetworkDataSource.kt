package com.eduardoflores.rolabox.device.podcasts.network.api

import arrow.core.Either

/**
 * The images that feeds name, for the episodes' artwork (ADR-031, Artwork). Nothing is sent but the request for the
 * image itself.
 */
interface ImageNetworkDataSource {
    /** The bytes of the image at [url], as the server sends them. They are only decoded where they are kept. */
    suspend fun fetch(url: String): Either<ImageNetworkError, ByteArray>
}

/** Why an image gave no bytes (ADR-007). */
sealed interface ImageNetworkError {
    /** No network, a connection that failed, or a timeout: worth trying again later. */
    data object Unreachable : ImageNetworkError

    /** An HTTP error, a URL that isn't one, or an image too large to keep: trying again won't help. */
    data object Unusable : ImageNetworkError
}
