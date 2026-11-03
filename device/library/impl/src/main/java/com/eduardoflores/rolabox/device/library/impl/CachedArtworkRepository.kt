package com.eduardoflores.rolabox.device.library.impl

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How many covers stay in memory: the song playing and the ones around it. */
internal const val CACHED_ARTWORK = 4

/**
 * The covers, through the index, which knows where each song's file is, and the system's thumbnail of it (ADR-026).
 * The last few are kept in memory, each for one version of its file, so a file that changed loads again. A song with
 * no cover is kept too, so it isn't looked for again. An error isn't kept.
 */
@Singleton
internal class CachedArtworkRepository @Inject constructor(
    private val index: LibraryIndexLocalDataSource,
    private val artwork: ArtworkLocalDataSource,
) : ArtworkRepository {
    private val mutex = Mutex()

    // In order of use, the least recent first.
    private val cache = object : LinkedHashMap<SongFile, Loaded>(CACHED_ARTWORK, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SongFile, Loaded>) = size > CACHED_ARTWORK
    }

    override suspend fun loadArtwork(ref: ArtworkRef): Either<LibraryError, ArtworkImage?> = either {
        val file = index.getSongFile(ref.song).bind() ?: return@either null
        mutex.withLock { cache[file] }?.let { return@either it.image }
        val image = artwork.loadArtwork(file.uri).bind()
        mutex.withLock { cache[file] = Loaded(image) }
        image
    }

    private class Loaded(val image: ArtworkImage?)
}

private const val LOAD_FACTOR = 0.75f
