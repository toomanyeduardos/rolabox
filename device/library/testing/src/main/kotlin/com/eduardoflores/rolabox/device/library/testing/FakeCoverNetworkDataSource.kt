package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.network.api.Cover
import com.eduardoflores.rolabox.device.library.network.api.CoverNetworkDataSource
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import com.eduardoflores.rolabox.device.library.network.api.CoverSourceError

/**
 * Answers that tests set by album in [answers]: an album with none set has no cover. Every request is kept in
 * [requests], so a test can tell that none was made.
 */
class FakeCoverNetworkDataSource : CoverNetworkDataSource {
    val answers = mutableMapOf<CoverQuery, Either<CoverSourceError, Cover?>>()
    val requests = mutableListOf<CoverQuery>()

    /** Called before each answer, for a test to change things while a request is out. */
    var onRequest: (CoverQuery) -> Unit = {}

    override suspend fun findCover(album: CoverQuery): Either<CoverSourceError, Cover?> {
        requests += album
        onRequest(album)
        return answers[album] ?: null.right()
    }
}
