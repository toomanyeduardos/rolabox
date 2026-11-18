package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkName
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Artwork files in memory, with no decoding: empty bytes aren't an image, and any others are kept as a one-pixel image
 * whose pixel is their size, so a test can tell which bytes were kept. [error] makes every call return it.
 */
class FakePodcastArtworkLocalDataSource : PodcastArtworkLocalDataSource {
    val saved = mutableMapOf<ArtworkName, ArtworkImage>()

    var error: PodcastError? = null

    private val savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun load(name: ArtworkName): Either<PodcastError, ArtworkImage?> =
        error?.left() ?: saved[name].right()

    override fun observeSaved(): Flow<Unit> = savedEvents.asSharedFlow()

    override suspend fun save(name: ArtworkName, bytes: ByteArray): Either<PodcastError, Boolean> {
        val failure = error
        val isImage = bytes.isNotEmpty()
        if (failure == null && isImage) {
            saved[name] = imageOf(bytes)
            savedEvents.tryEmit(Unit)
        }
        return failure?.left() ?: isImage.right()
    }

    companion object {
        /** The image [save] keeps for [bytes]. */
        fun imageOf(bytes: ByteArray) = ArtworkImage(1, 1, intArrayOf(bytes.size))
    }
}
