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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Covers that tests set by reference in [covers]: a reference with none set has no cover, and one set to a `Left`
 * fails. Setting one while it is observed is a cover that arrives, as a download's does. With [hold] set, a load waits
 * for it, as a slow one would.
 */
@Singleton
class FakeArtworkRepository @Inject constructor() : ArtworkRepository {
    private val state = MutableStateFlow<Map<ArtworkRef, Either<LibraryError, ArtworkImage?>>>(emptyMap())
    val covers: MutableMap<ArtworkRef, Either<LibraryError, ArtworkImage?>> = Covers()
    val loads = mutableListOf<ArtworkRef>()
    var hold: CompletableDeferred<Unit>? = null
    val downloadEnabled = MutableStateFlow(true)

    override suspend fun loadArtwork(ref: ArtworkRef): Either<LibraryError, ArtworkImage?> {
        loads += ref
        hold?.await()
        return state.value[ref] ?: null.right()
    }

    override fun observeArtwork(ref: ArtworkRef): Flow<Either<LibraryError, ArtworkImage?>> = flow {
        loads += ref
        hold?.await()
        emitAll(state.map { it[ref] ?: null.right() }.distinctUntilChanged())
    }

    override fun observeDownloadEnabled(): Flow<Either<LibraryError, Boolean>> = downloadEnabled.map { it.right() }

    override suspend fun setDownloadEnabled(enabled: Boolean): Either<LibraryError, Unit> {
        downloadEnabled.value = enabled
        return Unit.right()
    }

    // The map tests write, backed by a flow, so a cover set later reaches whoever observes it.
    private inner class Covers : AbstractMutableMap<ArtworkRef, Either<LibraryError, ArtworkImage?>>() {
        override val entries: MutableSet<MutableMap.MutableEntry<ArtworkRef, Either<LibraryError, ArtworkImage?>>>
            get() = state.value.toMutableMap().entries

        override fun put(key: ArtworkRef, value: Either<LibraryError, ArtworkImage?>) =
            state.value[key].also { state.value = state.value + (key to value) }
    }

    companion object {
        /** A cover of one [color], as ARGB, [size] pixels square. */
        fun solid(color: Int, size: Int = 2): ArtworkImage = ArtworkImage(size, size, IntArray(size * size) { color })
    }
}
