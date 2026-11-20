package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.AudioNetworkError

/**
 * Audio checks that tests fill in: the [answers] of each URL, and audio that is there for one with none. Every check is
 * recorded in [checked].
 */
class FakeAudioNetworkDataSource : AudioNetworkDataSource {
    var answers: Map<String, Either<AudioNetworkError, Unit>> = emptyMap()

    val checked = mutableListOf<String>()

    override suspend fun check(url: String): Either<AudioNetworkError, Unit> {
        checked += url
        return answers[url] ?: Unit.right()
    }
}
