package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreOutsideLibraryTest {
    private val rule = MediaStoreOutsideLibrary(Config.empty)

    @Test
    fun `flags an import of MediaStore, of something inside it, and a qualified use`() {
        val findings = rule.lint(
            """
            import android.provider.MediaStore
            import android.provider.MediaStore.Audio.Media

            class SongFinder {
                val collection = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            """.trimIndent(),
        )

        assertEquals(listOf(1, 2, 5), findings.map { it.entity.location.source.line })
        assertTrue(findings.all { it.message.startsWith("ADR-024 rule 2:") })
    }

    @Test
    fun `passes code that reads the library and other providers`() {
        val findings = rule.lint(
            """
            import android.provider.Settings
            import com.eduardoflores.rolabox.device.library.api.LibraryRepository

            class Songs(private val library: LibraryRepository) {
                fun songs() = library.observeSongs()
                val mediaStoreLike = "android.provider.MediaStore"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `flags a thumbnail loaded from the content resolver`() {
        val findings = rule.lint(
            """
            import android.content.Context
            import android.net.Uri
            import android.util.Size

            class Covers(private val context: Context) {
                fun cover(uri: Uri) = context.contentResolver.loadThumbnail(uri, Size(720, 720), null)
            }
            """.trimIndent(),
        )

        assertEquals(listOf(6), findings.map { it.entity.location.source.line })
        assertTrue(findings.single().message.startsWith("ADR-026 rule 3:"))
    }

    @Test
    fun `passes a cover read through the repository`() {
        val findings = rule.lint(
            """
            import com.eduardoflores.rolabox.device.library.api.ArtworkRef
            import com.eduardoflores.rolabox.device.library.api.ArtworkRepository

            class Covers(private val artwork: ArtworkRepository) {
                suspend fun cover(ref: ArtworkRef) = artwork.loadArtwork(ref)
                val name = "loadThumbnail"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
