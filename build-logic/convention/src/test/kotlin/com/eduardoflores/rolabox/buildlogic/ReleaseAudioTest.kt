package com.eduardoflores.rolabox.buildlogic

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The release audio check, over APKs built in the test: a planted MP3 fails it, the UI sounds pass. */
class ReleaseAudioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val wheelTick = "RIFF".ascii() + ByteArray(4) + "WAVEfmt ".ascii() + ByteArray(32) { it.toByte() }
    private val allowed = setOf(wheelTick.sha256())

    private val app = mapOf(
        "classes.dex" to "dex\n035\u0000".ascii() + ByteArray(64),
        "resources.arsc" to byteArrayOf(2, 0, 12, 0) + ByteArray(64),
        "res/mipmap-hdpi/ic_launcher.webp" to "RIFF".ascii() + ByteArray(4) + "WEBPVP8 ".ascii(),
        "res/drawable/photo.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
        "lib/arm64-v8a/libfoo.so" to byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()),
        // A release shortens resource paths, so the UI sound is allowed by its bytes, not its name.
        "res/qL.wav" to wheelTick,
    )

    @Test
    fun `a release with only the UI sounds passes`() {
        assertEquals(emptyList<String>(), releaseAudioViolations(apk(app), allowed))
    }

    @Test
    fun `a planted mp3 fails, by its tag or its frames, whatever its name`() {
        val tagged = "ID3".ascii() + byteArrayOf(4, 0, 0, 0, 0, 0, 0)
        val frames = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x44)

        val violations = releaseAudioViolations(
            apk(app + mapOf("assets/song.mp3" to tagged, "assets/data.bin" to frames, "res/raw/a.ogg" to ByteArray(8))),
            allowed,
        )

        assertEquals(
            listOf("assets/data.bin (MPEG audio)", "assets/song.mp3 (MP3 with an ID3 tag)", "res/raw/a.ogg (a .ogg file)"),
            violations,
        )
    }

    @Test
    fun `a wav that isn't one of the UI sounds fails`() {
        val otherWav = wheelTick.copyOf().also { it[it.size - 1] = 99 }

        assertEquals(
            listOf("res/x1.wav (WAV)"),
            releaseAudioViolations(apk(app + ("res/x1.wav" to otherWav)), allowed),
        )
    }

    @Test
    fun `the other audio formats are recognised by their bytes`() {
        assertEquals("M4A", audioKind("x", ByteArray(4) + "ftypM4A ".ascii()))
        assertEquals("AAC (ADTS)", audioKind("x", byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte())))
        assertEquals("Ogg", audioKind("x", "OggS".ascii()))
        assertEquals("FLAC", audioKind("x", "fLaC".ascii()))
        assertEquals("AMR", audioKind("x", "#!AMR\n".ascii()))
        assertEquals("MIDI", audioKind("x", "MThd".ascii()))
        assertNull(audioKind("x.png", byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
    }

    @Test
    fun `the allowed sounds are the design system's, and exist`() {
        // The tests run in build-logic/convention, two folders below the project.
        val project = File("../..")
        ALLOWED_RELEASE_AUDIO.forEach { path ->
            assertTrue(path, path.startsWith("common/designsystem/src/main/res/raw/ds_"))
            assertTrue(path, File(project, path).isFile)
        }
    }

    private fun apk(entries: Map<String, ByteArray>): File {
        val file = folder.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun String.ascii() = toByteArray(Charsets.ISO_8859_1)

    private fun ByteArray.sha256() =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}
