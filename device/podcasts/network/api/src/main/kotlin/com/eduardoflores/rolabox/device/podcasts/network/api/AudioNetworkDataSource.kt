package com.eduardoflores.rolabox.device.podcasts.network.api

import arrow.core.Either

/**
 * Whether the publisher's server gives an episode's audio, asked before an episode from search is offered for play
 * (ADR-031, Search). It goes to the publisher, not to Apple's directory, and nothing is sent but the request itself.
 */
interface AudioNetworkDataSource {
    /**
     * Asks for the first kilobyte of the audio at [url] (`Range: bytes=0-1023`) and reads none of the body: this isn't
     * a download. Any answer but a refusal is `Unit`: a 206, a 200 from a server that ignores the range, and an error
     * that isn't a refusal, which the player reports when it meets it.
     */
    suspend fun check(url: String): Either<AudioNetworkError, Unit>
}

/** Why the audio isn't known to be there (ADR-007). */
sealed interface AudioNetworkError {
    /** No network, a connection that failed, or a timeout. */
    data object Unreachable : AudioNetworkError

    /** The server refused the audio: a 401, 403 or 410, as a paid episode kept in a public feed gets. */
    data object Refused : AudioNetworkError
}
