package com.eduardoflores.rolabox.device.podcasts.local.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkName
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource.Companion.ARTWORK_SIZE
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/** The folder of the artwork, in the app's files, which the system doesn't clear as it can the cache (ADR-031). */
internal const val PODCAST_ARTWORK_FOLDER = "podcasts/artwork"

private const val JPEG_QUALITY = 90

/**
 * The artwork as JPEG files in [folder], each at most [ARTWORK_SIZE] pixels on its longest side, decoded with a sample
 * size and then scaled, so a 3,000 pixel image is never held whole (ADR-031, Artwork; ADR-026, rule 4: no image
 * library). A file is written next to where it goes and renamed, so a file that is there is whole. It is a singleton,
 * so every observer hears of each image kept.
 */
@Singleton
internal class FilePodcastArtworkLocalDataSource(
    private val folder: File,
    private val ioDispatcher: CoroutineDispatcher,
) : PodcastArtworkLocalDataSource {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.filesDir, PODCAST_ARTWORK_FOLDER), ioDispatcher)

    private val saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun load(name: ArtworkName): Either<PodcastError, ArtworkImage?> = withContext(ioDispatcher) {
        val file = File(folder, name.fileName())
        // A file that is gone, or isn't an image, decodes to nothing.
        val bitmap = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
        bitmap?.toArtworkImage().right()
    }

    override fun observeSaved(): Flow<Unit> = saved.asSharedFlow()

    override suspend fun save(name: ArtworkName, bytes: ByteArray): Either<PodcastError, Boolean> =
        withContext(ioDispatcher) {
            val image = decodeDownTo(bytes) ?: return@withContext false.right()
            catchNamed(::toFileError) {
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Couldn't make $folder")
                val fileName = name.fileName()
                val partial = File(folder, "$fileName.partial")
                partial.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (!partial.renameTo(File(folder, fileName))) {
                    partial.delete()
                    throw IOException("Couldn't keep $fileName")
                }
                saved.tryEmit(Unit)
                true
            }
        }
}

/** The image of [bytes], at most [ARTWORK_SIZE] pixels on its longest side, or `null` when they aren't one. */
internal fun decodeDownTo(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sampled = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } else {
        null
    }
    return sampled?.scaledDownTo(ARTWORK_SIZE)
}

/** This bitmap, scaled to at most [size] pixels on its longest side, or itself when it is no larger. */
private fun Bitmap.scaledDownTo(size: Int): Bitmap {
    val scale = size.toFloat() / maxOf(width, height)
    if (scale >= 1f) return this
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true).also { if (it !== this) recycle() }
}

/** The power of two that brings [longestSide] down to no less than [ARTWORK_SIZE], as `BitmapFactory` samples. */
internal fun sampleSizeFor(longestSide: Int): Int {
    var sampleSize = 1
    while (longestSide / (sampleSize * 2) >= ARTWORK_SIZE) sampleSize *= 2
    return sampleSize
}

/**
 * The file of an artwork: its kind and id, and a hash of its URL, so an image the feed changes is a new file and one
 * whose name doesn't match the URL any more is downloaded again (ADR-031, Artwork).
 */
internal fun ArtworkName.fileName(): String {
    val owner = when (this) {
        is ArtworkName.OfEpisode -> "episode-${episodeId.value}"
        is ArtworkName.OfShow -> "show-${showId.value}"
    }
    return "$owner-${url.sha256().take(HASH_LENGTH)}.jpg"
}

private const val HASH_LENGTH = 16

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(Locale.ROOT, it) }

private fun Bitmap.toArtworkImage(): ArtworkImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return ArtworkImage(width, height, pixels)
}

private fun toFileError(error: Throwable): PodcastError? = when (error) {
    is IOException -> PodcastError.Storage(StorageError.Unavailable)
    else -> null
}
