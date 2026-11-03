package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/**
 * Covers that tests set by reference in [covers]: a reference with none set has no cover, and one set to a `Left`
 * fails. With [hold] set, a load waits for it, as a slow one would.
 */
@Singleton
class FakeArtworkRepository @Inject constructor() : ArtworkRepository {
    val covers = mutableMapOf<ArtworkRef, Either<LibraryError, ArtworkImage?>>()
    val loads = mutableListOf<ArtworkRef>()
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun loadArtwork(ref: ArtworkRef): Either<LibraryError, ArtworkImage?> {
        loads += ref
        hold?.await()
        return covers[ref] ?: null.right()
    }

    companion object {
        /** A cover of one [color], as ARGB, [size] pixels square. */
        fun solid(color: Int, size: Int = 2): ArtworkImage = ArtworkImage(size, size, IntArray(size * size) { color })
    }
}
