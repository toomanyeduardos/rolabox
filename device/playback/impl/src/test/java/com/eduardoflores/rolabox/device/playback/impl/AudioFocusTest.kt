package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
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
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What the player does when the phone needs the audio (ADR-023, "Audio focus, noise and buttons"), and what
 * [MediaPlayback] says about it. The real player of [buildPlayer], as in [MediaPlaybackTest], and what the system
 * would tell it is told by hand. That the real system tells it is checked by hand on a device.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class AudioFocusTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = TestScope()
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
        player = buildPlayer(context) {
            setClock(FakeClock(true))
            // As in MediaPlaybackTest: the fake clock runs ahead, and nothing is stuck here.
            setStuckBufferingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingNotEndingTimeoutMs(Int.MAX_VALUE)
            setStuckSuppressedDetectionTimeoutMs(Int.MAX_VALUE)
        }
        val connection = object : PlayerConnection {
            override val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher

            override suspend fun player() = player.right()
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
    fun losingTheFocusForAWhile_pausesTheItem_andItResumesWhenTheFocusReturns() = scope.runTest {
        playing()

        focusChanges(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        RobolectricUtil.runMainLooperUntil { player.isSuppressed() }
        tick()
        assertFalse(playback.isPlaying.first())

        focusChanges(AudioManager.AUDIOFOCUS_GAIN)
        RobolectricUtil.runMainLooperUntil { !player.isSuppressed() }
        tick()
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun losingTheFocusForAWhile_stillPlaysOnWithoutTheApp_soItResumesAfterACall() = scope.runTest {
        playing()

        focusChanges(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        RobolectricUtil.runMainLooperUntil { player.isSuppressed() }

        assertFalse(player.isPlaying)
        assertTrue(player.playsOn())
    }

    @Test
    fun losingTheFocusForGood_pausesTheItem_andNothingResumesIt() = scope.runTest {
        playing()

        focusChanges(AudioManager.AUDIOFOCUS_LOSS)
        RobolectricUtil.runMainLooperUntil { !player.playWhenReady }
        tick()
        assertFalse(playback.isPlaying.first())
        assertFalse(player.playsOn())

        focusChanges(AudioManager.AUDIOFOCUS_GAIN)
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)
        tick()
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun aShortSoundThatDucks_leavesTheItemPlaying() = scope.runTest {
        playing()

        focusChanges(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)
        tick()

        assertTrue(playback.isPlaying.first())
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, player.playbackSuppressionReason)
    }

    @Test
    fun theHeadphonesGoing_pausesTheItem() = scope.runTest {
        playing()

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        RobolectricUtil.runMainLooperUntil { !player.playWhenReady }
        tick()

        assertFalse(playback.isPlaying.first())
    }

    private suspend fun TestScope.playing() {
        playback.play(PlaybackSource.AllSongs, PlaybackItemId.Song(SONG))
        TestPlayerRunHelper.playUntilPosition(player, 0, 1.seconds.inWholeMilliseconds)
        tick()
    }

    private fun Player.isSuppressed() = playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE

    /** Tells the player what the system tells the app that holds the focus. */
    private fun focusChanges(change: Int) {
        val audio = shadowOf(context.getSystemService(AudioManager::class.java))
        RobolectricUtil.runMainLooperUntil { audio.lastAudioFocusRequest != null }
        audio.lastAudioFocusRequest.listener.onAudioFocusChange(change)
    }

    /** Lets the player's events reach their listeners, and then one [Tick] of the test's clock, which reads it. */
    private fun TestScope.tick() {
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(Tick)
        runCurrent()
    }

    private companion object {
        val SONG = SongId(1)
    }
}
