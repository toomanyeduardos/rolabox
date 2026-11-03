package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource

/** Covers that tests set by a file's URI. A file with none set has no cover. Every load is kept in [loads]. */
class FakeArtworkLocalDataSource : ArtworkLocalDataSource {
    val results = mutableMapOf<String, Either<LibraryError, ArtworkImage?>>()
    val loads = mutableListOf<String>()

    override suspend fun loadArtwork(uri: String): Either<LibraryError, ArtworkImage?> {
        loads += uri
        return results[uri] ?: null.right()
    }
}
