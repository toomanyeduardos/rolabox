package com.eduardoflores.rolabox.device.playback.impl

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.SongId
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ARTWORK_SCHEME = "rolabox-artwork"
private const val SONG = "song"
private const val VERSION = "v"

/**
 * The URI an item's `MediaMetadata` gives for its artwork: a name for [ref] that only [ArtworkBitmapLoader] reads, with
 * the [version] of its artwork once that has changed while it played.
 * Media3 hands the platform the bitmap it loaded. The platform's session also gets the URI, which the system can't
 * read: with no artwork, its media controls log a warning and show their own background (ADR-026).
 */
internal fun artworkUriOf(ref: ArtworkRef, version: Int = 0): Uri = Uri.Builder()
    .scheme(ARTWORK_SCHEME)
    .authority(SONG)
    .appendPath(ref.song.value.toString())
    // A cover that arrived is a new URI, so Media3 doesn't keep the bitmap it loaded before (ADR-027).
    .apply { if (version > 0) appendQueryParameter(VERSION, version.toString()) }
    .build()

internal fun artworkRefOf(uri: Uri): ArtworkRef? = uri.takeIf { it.scheme == ARTWORK_SCHEME && it.authority == SONG }
    ?.lastPathSegment
    ?.toLongOrNull()
    ?.let { ArtworkRef(SongId(it)) }

/**
 * What the session draws the notification's and the lock screen's artwork with: Rolabox's own URIs through
 * [ArtworkRepository], so they show what Now Playing shows (ADR-026, rule 6). Anything else goes to [fallback], which
 * is also what decodes a picture the player read from the file itself. No artwork fails the load, which Media3 takes as
 * none.
 */
@OptIn(UnstableApi::class)
internal class ArtworkBitmapLoader(
    private val artwork: ArtworkRepository,
    private val fallback: BitmapLoader,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val ref = artworkRefOf(uri) ?: return fallback.loadBitmap(uri)
        // Failing the future, not the coroutine, which would take the scope down with it.
        val bitmap = SettableFuture.create<Bitmap>()
        scope.launch {
            val image = artwork.loadArtwork(ref).getOrNull()
            if (image != null) bitmap.set(image.toBitmap()) else bitmap.setException(IOException("No artwork for $ref"))
        }.invokeOnCompletion { bitmap.cancel(false) }
        return bitmap
    }
}

internal fun ArtworkImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
