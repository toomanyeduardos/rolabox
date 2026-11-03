package com.eduardoflores.rolabox.device.playback.impl

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtworkBitmapLoaderTest {
    private val artwork = FakeArtworkRepository()
    private val fallbackLoads = mutableListOf<Uri>()
    private val fallback = object : BitmapLoader {
        override fun supportsMimeType(mimeType: String) = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = Futures.immediateFuture(bitmap())

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            fallbackLoads += uri
            return Futures.immediateFuture(bitmap())
        }
    }
    private val scope = TestScope()
    private val loader = ArtworkBitmapLoader(artwork, fallback, scope.backgroundScope)
    private val ref = ArtworkRef(SongId(7))

    private fun bitmap() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private fun TestScope.load(uri: Uri): ListenableFuture<Bitmap> = loader.loadBitmap(uri).also { runCurrent() }

    @Test
    fun anItemsArtworkUri_namesItsRef() {
        assertEquals(ref, artworkRefOf(artworkUriOf(ref)))
        // A new version is a new URI for the same song (ADR-027).
        assertEquals(ref, artworkRefOf(artworkUriOf(ref, version = 2)))
        assertNotEquals(artworkUriOf(ref), artworkUriOf(ref, version = 2))
        assertNull(artworkRefOf(Uri.parse("content://media/external/audio/media/7")))
    }

    @Test
    fun rolaboxsUri_loadsTheCoverFromTheRepository() = scope.runTest {
        artwork.covers[ref] = FakeArtworkRepository.solid(RED, size = 3).right()

        val bitmap = load(artworkUriOf(ref)).get()

        assertEquals(3, bitmap.width)
        assertEquals(RED, bitmap.getPixel(1, 1))
        assertEquals(listOf(ref), artwork.loads)
        assertTrue(fallbackLoads.isEmpty())
    }

    @Test
    fun noCover_orOneThatFailed_failsTheLoad_whichMedia3TakesAsNone() = scope.runTest {
        assertThrows(ExecutionException::class.java) { load(artworkUriOf(ref)).get().recycle() }

        artwork.covers[ref] = LibraryError.FileUnreadable("content://song").left()
        assertThrows(ExecutionException::class.java) { load(artworkUriOf(ref)).get().recycle() }
    }

    @Test
    fun anyOtherUri_goesToMedia3sOwnLoader() = scope.runTest {
        val uri = Uri.parse("https://example.com/cover.jpg")

        assertEquals(1, load(uri).get().width)
        assertEquals(listOf(uri), fallbackLoads)
        assertTrue(artwork.loads.isEmpty())
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
    }
}
