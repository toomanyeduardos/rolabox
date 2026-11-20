package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ADR-034, rule 4, for the episodes' HTTP: [Host] answers each URL as a host would, with no network. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class HttpsFirstDataSourceTest {
    private val host = Host()
    private val source = HttpsFirstDataSource.Factory { host }.createDataSource()

    @After
    fun close() = source.close()

    @Test
    fun anHttpEnclosure_isOpenedAsHttps() {
        source.open(DataSpec(Uri.parse("http://media.example.com/episode.mp3?id=1")))

        assertEquals(listOf("https://media.example.com/episode.mp3?id=1"), host.opened)
        assertEquals("https://media.example.com/episode.mp3?id=1", source.uri.toString())
    }

    @Test
    fun aHandshakeThatFails_fallsBackToTheUrlAsGiven() {
        host.failing = { it.scheme == "https" }

        source.open(DataSpec(Uri.parse("http://media.example.com/episode.mp3")))

        assertEquals(
            listOf("https://media.example.com/episode.mp3", "http://media.example.com/episode.mp3"),
            host.opened,
        )
        assertEquals(1, host.closed)
        assertEquals("http://media.example.com/episode.mp3", source.uri.toString())
    }

    @Test
    fun anHttpsUrl_andAPortOfItsOwn_areOpenedAsGiven() {
        listOf("https://media.example.com/episode.mp3", "http://media.example.com:8080/episode.mp3").forEach {
            source.open(DataSpec(Uri.parse(it)))
            source.close()
        }

        assertEquals(
            listOf("https://media.example.com/episode.mp3", "http://media.example.com:8080/episode.mp3"),
            host.opened,
        )
    }

    @Test
    fun anInterruptedOpen_isNotRetried() {
        host.failing = {
            Thread.currentThread().interrupt()
            true
        }

        try {
            assertThrows(IOException::class.java) {
                source.open(DataSpec(Uri.parse("http://media.example.com/episode.mp3")))
            }
        } finally {
            // Clears the flag, so the next test's thread isn't interrupted.
            Thread.interrupted()
        }
        assertEquals(listOf("https://media.example.com/episode.mp3"), host.opened)
    }

    /** Fails to open the URIs that [failing] says, as a host that doesn't serve HTTPS. */
    private class Host : DataSource {
        val opened = mutableListOf<String>()
        var closed = 0
        var failing: (Uri) -> Boolean = { false }
        private var current: Uri? = null

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            opened += dataSpec.uri.toString()
            if (failing(dataSpec.uri)) {
                throw HttpDataSource.HttpDataSourceException(
                    SSLHandshakeException("no certificate for this host"),
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            }
            current = dataSpec.uri
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1

        override fun getUri(): Uri? = current

        override fun close() {
            closed++
            current = null
        }
    }
}
