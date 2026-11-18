package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.eduardoflores.rolabox.common.userdata.api.Repeat
import com.eduardoflores.rolabox.common.userdata.api.Shuffle
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import com.eduardoflores.rolabox.device.playback.impl.downloads.episodeCacheKey
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import com.eduardoflores.rolabox.device.playback.testing.FakePlaybackLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.testing.FakeEpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Episodes on the real player (ADR-031, Episodes in playback): a real [ExoPlayer] with Media3's fake clock, playing an
 * MP3 of library/02's fixtures as an episode's audio, behind [MediaPlayback]. The podcasts are fakes, which record the
 * positions playback stores.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class EpisodePlaybackTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = TestScope()
    private val podcasts = FakePodcastsRepository()
    private val progress = FakeEpisodeProgressRepository()
    private val local = FakePlaybackLocalDataSource()
    private val userData = FakeUserDataRepository()
    private val downloader = FakeEpisodeDownloader()
    private lateinit var episode: Episode
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        val file = File(folder.root, "episode.mp3")
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = LENGTH_MS)).bytes())
        episode =
            PodcastSamples.unplayed.copy(audioUrl = Uri.fromFile(file).toString(), length = LENGTH_MS.milliseconds)
        podcasts.owners = PodcastSamples.owners
        podcasts.shows = PodcastSamples.shows
        podcasts.episodes = listOf(episode)
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
            local,
            scope.backgroundScope,
            scope.testScheduler.timeSource,
            FakeArtworkRepository(),
            userData,
            PlayOrders(Random(0)),
            FakeNonFatalReport(),
            Podcasts(podcasts, progress, FakePodcastArtworkRepository()),
            downloader,
        )
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun play_queuesTheEpisodeAlone_withItsShowAndOwner() = scope.runTest {
        val result = playback.play(source, item)

        assertTrue(result.isRight())
        assertEquals(listOf(item), playback.queue.first().map { it.id })
        val loaded = playback.currentItem.first()
        assertEquals(episode.title, loaded?.title)
        assertEquals(PodcastSamples.theDaily.title, loaded?.artist)
        assertEquals(PodcastSamples.nyTimes.name, loaded?.album)
        assertNull(loaded?.artwork)
        assertEquals(1, player.mediaItemCount)
        assertEquals(Uri.parse(episode.audioUrl), player.currentMediaItem?.localConfiguration?.uri)
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, player.currentMediaItem?.mediaMetadata?.mediaType)
    }

    @Test
    fun play_addsTheEpisodesDownload_andReadsItThroughTheCacheByItsId() = scope.runTest {
        playback.play(source, item)

        assertEquals(
            listOf(FakeEpisodeDownloader.Call(episode.id, episode.audioUrl, episode.audioSizeBytes)),
            downloader.calls,
        )
        assertEquals(episodeCacheKey(episode.id), player.currentMediaItem?.localConfiguration?.customCacheKey)
    }

    @Test
    fun play_withNoRoomForTheDownload_loadsNothing() = scope.runTest {
        downloader.result = PlaybackError.StorageFull.left()

        val result = playback.play(source, item)

        assertEquals(PlaybackError.StorageFull, result.leftOrNull())
        assertEquals(0, player.mediaItemCount)
        assertNull(playback.currentItem.first())
    }

    @Test
    fun theLoadedEpisodesDownload_isTheState() = scope.runTest {
        downloader.downloads.value = mapOf(item to ItemDownload.Downloading(0.4f))
        assertNull(playback.download.first())

        playback.play(source, item)

        assertEquals(ItemDownload.Downloading(0.4f), playback.download.first())
        downloader.downloads.value = mapOf(item to ItemDownload.Done)
        assertEquals(ItemDownload.Done, playback.download.first())
    }

    @Test
    fun resume_startsWhereTheEpisodeWasLeft_andPlayStartsOver() = scope.runTest {
        progress.positions[episode.id] = 20.seconds

        playback.play(source, item, resume = true)
        assertNear(20.seconds, player.currentPosition.milliseconds)

        playback.play(source, item)
        assertNear(Duration.ZERO, player.currentPosition.milliseconds)
        // Starting over is where the episode is now, as Play from Start means.
        runCurrent()
        assertNear(Duration.ZERO, progress.positions.getValue(episode.id))
    }

    @Test
    fun play_ofAnEpisodeThatIsGone_loadsNothing() = scope.runTest {
        val gone = EpisodeId(999)

        val result = playback.play(PlaybackSource.Episode(gone), PlaybackItemId.Episode(gone))

        assertEquals(PlaybackError.Podcasts(PodcastError.EpisodeNotFound(gone)), result.leftOrNull())
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun aPositionThatCantBeRead_failsTheResume() = scope.runTest {
        progress.error = PodcastError.Storage(StorageError.Unavailable)

        val result = playback.play(source, item, resume = true)

        assertEquals(PlaybackError.Podcasts(PodcastError.Storage(StorageError.Unavailable)), result.leftOrNull())
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun next_skipsForward30s_andPrevious_skipsBack15s() = scope.runTest {
        playback.play(source, item)
        ready()
        playback.pause()
        playback.seekTo(5.seconds)

        playback.next()
        idle()
        assertNear(35.seconds, player.currentPosition.milliseconds)

        playback.previous()
        idle()
        assertNear(20.seconds, player.currentPosition.milliseconds)
        assertEquals(item, playback.currentItem.first()?.id)
    }

    @Test
    fun previous_stopsAtTheStart_andStartsNothingAgain() = scope.runTest {
        playback.play(source, item)
        ready()
        playback.pause()
        playback.seekTo(5.seconds)
        tick()
        val starts = playback.startCount.first()

        playback.previous()
        idle()
        tick()

        assertNear(Duration.ZERO, player.currentPosition.milliseconds)
        assertEquals(starts, playback.startCount.first())
    }

    @Test
    fun next_inTheLast30s_endsTheEpisode_whichIsPlayed() = scope.runTest {
        playback.play(source, item)
        ready()
        playback.seekTo(LENGTH_MS.milliseconds - 10.seconds)

        playback.next()
        TestPlayerRunHelper.run(player).untilState(Player.STATE_ENDED)
        tick()

        assertNull(playback.currentItem.first())
        assertTrue(episode.id in progress.ended)
        assertNull(local.point)
    }

    @Test
    fun aHold_scrubsAsForASong() = scope.runTest {
        playback.play(source, item)
        ready()
        playback.pause()

        playback.startSeeking(SeekDirection.Forward)
        advanceTimeBy(1.seconds)
        runCurrent()
        playback.stopSeeking()
        idle()

        assertNear((SEEK_SPEED).seconds, player.currentPosition.milliseconds)
    }

    @Test
    fun anEpisode_isNeitherShuffledNorRepeated_whateverTheSettingsSay() = scope.runTest {
        userData.setShuffle(Shuffle.SONGS)
        userData.setRepeat(Repeat.ALL)
        runCurrent()

        playback.play(source, item)
        tick()

        assertFalse(playback.isShuffled.first())
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        assertFalse(player.shuffleModeEnabled)
        // The setting is still the setting: the header hides its icon for an episode.
        assertEquals(RepeatMode.All, playback.repeatMode.first())

        userData.setRepeat(Repeat.ONE)
        tick()
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    @Test
    fun pausing_storesTheEpisodesPosition_withTheLengthThePlayerKnows() = scope.runTest {
        playback.play(source, item)
        TestPlayerRunHelper.playUntilPosition(player, 0, 3.seconds.inWholeMilliseconds)
        tick()

        playback.pause()
        runCurrent()

        assertNear(3.seconds, progress.positions.getValue(episode.id))
        assertNear(LENGTH_MS.milliseconds, progress.lengths.getValue(episode.id)!!)
        assertEquals(item, local.point?.item)
        assertEquals(source, local.point?.source)
    }

    @Test
    fun playingToTheEnd_marksTheEpisodePlayed_andNothingPlaysAfterIt() = scope.runTest {
        playback.play(source, item)
        playback.seekTo(LENGTH_MS.milliseconds - 1.seconds)

        TestPlayerRunHelper.run(player).untilState(Player.STATE_ENDED)
        tick()

        assertTrue(episode.id in progress.ended)
        assertNull(playback.currentItem.first())
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun aStoredEpisode_isRestoredPaused_atThePodcastsPosition() = scope.runTest {
        local.point = ResumePoint(source, item, 0, 5.seconds)
        progress.positions[episode.id] = 25.seconds

        playback.restore()
        tick()

        assertEquals(item, playback.currentItem.first()?.id)
        assertFalse(playback.isPlaying.first())
        assertNear(25.seconds, player.currentPosition.milliseconds)
    }

    @Test
    fun aStoredEpisodeThatIsGone_isForgotten() = scope.runTest {
        val gone = EpisodeId(999)
        local.point = ResumePoint(PlaybackSource.Episode(gone), PlaybackItemId.Episode(gone), 0, 5.seconds)

        playback.restore()
        tick()

        assertNull(playback.currentItem.first())
        assertNull(local.point)
    }

    private val source get() = PlaybackSource.Episode(episode.id)

    private val item get() = PlaybackItemId.Episode(episode.id)

    private fun ready() {
        TestPlayerRunHelper.run(player).untilState(Player.STATE_READY)
    }

    private fun idle() {
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)
    }

    private fun TestScope.tick() {
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(Tick)
        runCurrent()
    }

    private fun assertNear(expected: Duration, actual: Duration) {
        assertTrue("Expected $expected, was $actual", abs((actual - expected).inWholeMilliseconds) <= NEAR_MS)
    }

    private companion object {
        const val LENGTH_MS = 45_000
        const val NEAR_MS = 150
    }
}
