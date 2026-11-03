package com.eduardoflores.rolabox.device.library.local.impl.mediastore

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Size
import arrow.core.Either
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource.Companion.ARTWORK_SIZE
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "LibraryArtwork"

/**
 * MediaStore's thumbnail of a song (ADR-026). The media provider makes it: the picture embedded in the tags, or else
 * the best `.jpg` or `.png` in the song's folder, which it reads itself, so the audio permission is enough.
 */
internal class MediaStoreArtworkLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ArtworkLocalDataSource {
    override suspend fun loadArtwork(uri: String): Either<LibraryError, ArtworkImage?> = withContext(ioDispatcher) {
        catchNamed({ error -> toLibraryError(error, uri) }) {
            try {
                context.contentResolver.loadThumbnail(Uri.parse(uri), Size(ARTWORK_SIZE, ARTWORK_SIZE), null)
                    .toArtworkImage()
            } catch (_: IOException) {
                // How the provider says there is no artwork. A file that is gone says the same, and has none either.
                null
            }
        }
    }
}

private fun Bitmap.toArtworkImage(): ArtworkImage {
    // A hardware bitmap's pixels can't be read, so it is copied to memory first.
    val readable = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val pixels = IntArray(readable.width * readable.height)
    readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
    return ArtworkImage(readable.width, readable.height, pixels)
}

private fun toLibraryError(error: Throwable, uri: String): LibraryError? = when (error) {
    // The permission went away, or the system wouldn't open the file.
    is SecurityException, is IllegalArgumentException -> {
        Log.w(TAG, "Couldn't load the artwork of $uri", error)
        LibraryError.FileUnreadable(uri)
    }

    else -> null
}
