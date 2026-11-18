package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import arrow.core.right
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.impl.downloads.FakeEpisodeServer
import com.eduardoflores.rolabox.device.playback.testing.FakePlaybackLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakeEpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * An episode that runs out of bytes waits for the network, still playing, and goes on by itself (ADR-031, rule 15): a
 * real [ExoPlayer] streaming from [FakeEpisodeServer], behind [MediaPlayback]. What it waits for is the downloads'
 * [FakeEpisodeDownloader.waitsFor], which the tests set as the network and Use Mobile Data would.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class NetworkWaitTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = TestScope()
    private val server = FakeEpisodeServer(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = 30_000)).bytes())
    private val downloader = FakeEpisodeDownloader()
    private val episode = PodcastSamples.unplayed.copy(audioUrl = server.url)
    private val source = PlaybackSource.Episode(episode.id)
    private val item = PlaybackItemId.Episode(episode.id)
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        val podcasts = FakePodcastsRepository().apply {
            owners = PodcastSamples.owners
            shows = PodcastSamples.shows
            episodes = listOf(episode)
        }
        player = buildPlayer(context) {
            setClock(FakeClock(true))
            setStuckBufferingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingNotEndingTimeoutMs(Int.MAX_VALUE)
            setStuckSuppressedDetectionTimeoutMs(Int.MAX_VALUE)
        }
        val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val connection = object : PlayerConnection {
            override val dispatcher = dispatcher

            override suspend fun player() = player.right()
        }
        playback = MediaPlayback(
            FakeLibraryRepository(),
            connection,
            PhoneVolume(context),
            FakePlaybackLocalDataSource(),
            scope.backgroundScope,
            scope.testScheduler.timeSource,
            FakeArtworkRepository(),
            FakeUserDataRepository(),
            PlayOrders(Random(0)),
            FakeNonFatalReport(),
            Podcasts(podcasts, FakeEpisodeProgressRepository(), FakePodcastArtworkRepository()),
            downloader,
        )
    }

    @After
    fun tearDown() {
        player.release()
        server.close()
    }

    @Test
    fun noConnection_waits_stillPlaying_andGoesOnWhenThereIsOne() = scope.runTest {
        downloader.waitsFor.value = PlaybackError.NoConnection
        server.offline = true

        lostConnection()

        assertEquals(PlaybackError.NoConnection, playback.waitingFor.first())
        assertTrue(playback.isPlaying.first())
        assertEquals(item, playback.currentItem.first()?.id)
        assertNull(playback.skipped.first())

        server.offline = false
        downloader.waitsFor.value = null
        runCurrent()
        TestPlayerRunHelper.run(player).untilState(Player.STATE_READY)
        settle()

        assertNull(playback.waitingFor.first())
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun mobileDataOff_isWhatItWaitsFor_andTheReasonFollowsTheNetwork() = scope.runTest {
        downloader.waitsFor.value = PlaybackError.MobileDataOff
        server.offline = true

        lostConnection()
        assertEquals(PlaybackError.MobileDataOff, playback.waitingFor.first())

        downloader.waitsFor.value = PlaybackError.NoConnection
        settle()

        assertEquals(PlaybackError.NoConnection, playback.waitingFor.first())
    }

    @Test
    fun pausing_endsTheWait_andThePausedEpisodeDoesntGoOnByItself() = scope.runTest {
        downloader.waitsFor.value = PlaybackError.NoConnection
        server.offline = true
        lostConnection()

        playback.pause()
        server.offline = false
        downloader.waitsFor.value = null
        settle()

        assertNull(playback.waitingFor.first())
        assertFalse(playback.isPlaying.first())
        assertEquals(Player.STATE_IDLE, player.playbackState)

        playback.resume()
        TestPlayerRunHelper.run(player).untilState(Player.STATE_READY)
        settle()

        assertTrue(playback.isPlaying.first())
    }

    private suspend fun TestScope.lostConnection() {
        playback.play(source, item)
        TestPlayerRunHelper.run(player).untilPlayerError()
        settle()
    }

    private fun TestScope.settle() {
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }
}
