package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.test.core.app.ApplicationProvider
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What [MediaPlayback] does when the session can't be connected to: a typed error, and nothing thrown (ADR-007). */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class PlayerUnavailableTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = TestScope()
    private var connected = false
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        val artist = Artist(ArtistId(1), "Artist")
        val album = Album(AlbumId(1), artist.id, "Album", 2000)
        val file = File(folder.root, "song.mp3")
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = 20_000)).bytes())
        val library = FakeLibraryRepository().apply {
            artists = listOf(artist)
            albums = listOf(album)
            songs = listOf(Song(SONG, album.id, artist.id, "Song", 1, 20.seconds, Uri.fromFile(file).toString()))
        }
        player = buildPlayer(context) { setClock(FakeClock(true)) }
        val connection = object : PlayerConnection {
            override val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher

            override suspend fun player(): Either<PlaybackError.PlayerUnavailable, ExoPlayer> =
                if (connected) player.right() else PlaybackError.PlayerUnavailable.left()
        }
        playback =
            MediaPlayback(
                library,
                connection,
                PhoneVolume(context),
                scope.backgroundScope,
                scope.testScheduler.timeSource,
            )
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun play_withoutAConnectionFailsWithTheError_andNothingIsLoaded() = scope.runTest {
        val result = playback.play(PlaybackSource.AllSongs, ITEM)

        assertEquals(PlaybackError.PlayerUnavailable.left(), result)
        assertNull(playback.currentItem.first())
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun theOtherActions_withoutAConnectionDoNothing() = scope.runTest {
        playback.pause()
        playback.resume()
        playback.next()
        playback.previous()
        playback.seekTo(1.seconds)
        playback.changeVolume(1)
        runCurrent()

        assertNull(playback.currentItem.first())
    }

    @Test
    fun losingTheConnectionForGood_leavesNothingLoaded() = scope.runTest {
        connected = true
        playback.play(PlaybackSource.AllSongs, ITEM)
        assertTrue(playback.isPlaying.first())

        connected = false
        playback.pause()

        assertNull(playback.currentItem.first())
        assertFalse(playback.isPlaying.first())
        assertEquals(emptyList<Any>(), playback.queue.first())
    }

    @Test
    fun aConnectionThatComesBack_playsAgain() = scope.runTest {
        assertEquals(PlaybackError.PlayerUnavailable.left(), playback.play(PlaybackSource.AllSongs, ITEM))

        connected = true

        assertEquals(Unit.right(), playback.play(PlaybackSource.AllSongs, ITEM))
        assertEquals(ITEM, playback.currentItem.first()?.id)
    }

    private companion object {
        val SONG = SongId(1)
        val ITEM = PlaybackItemId.Song(SONG)
    }
}
