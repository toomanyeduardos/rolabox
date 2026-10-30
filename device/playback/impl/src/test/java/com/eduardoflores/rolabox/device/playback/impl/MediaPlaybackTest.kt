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
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.abs
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
 * The real player (ADR-023): a real [ExoPlayer] with Media3's fake clock, playing MP3s of library/02's fixtures,
 * behind [MediaPlayback]. The coroutines, the ticks and the holds run on the test's clock, and the player plays on
 * its own, so a test lets the player play with [playTo] and then lets a [Tick] read it.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class MediaPlaybackTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val artist = Artist(ArtistId(1), "Artist")
    private val album = Album(AlbumId(1), artist.id, "Album", 2000)
    private val otherAlbum = Album(AlbumId(2), artist.id, "Other album", 2001)
    private val library = FakeLibraryRepository()
    private val scope = TestScope()
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        library.artists = listOf(artist)
        library.albums = listOf(album, otherAlbum)
        library.songs = (1..5).map { song(it.toLong(), album, length = (10 + it).seconds) } +
            song(6, otherAlbum, length = 20.seconds)
        // Halfway, as a phone's volume is before anything plays here.
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2,
            0,
        )
        player = buildPlayer(context) {
            setClock(FakeClock(true))
            // The fake clock runs ahead whenever the player's threads wait. On a busy machine they wait long
            // enough for Media3 to take a player that is only slow for a stuck one, so nothing is stuck here.
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
    fun beforeAnythingIsPlayed_nothingIsLoaded() = scope.runTest {
        assertNull(playback.currentItem.first())
        assertNull(playback.queueIndex.first())
        assertEquals(emptyList<Any>(), playback.queue.first())
        assertEquals(Duration.ZERO, playback.position.first())
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun beforeAnythingIsPlayed_actionsDoNothing() = scope.runTest {
        playback.seekTo(3.seconds)
        playback.next()
        playback.previous()
        playback.pause()
        playback.resume()
        playback.startSeeking(SeekDirection.Forward)
        tick()
        playback.stopSeeking()

        assertNull(playback.currentItem.first())
        assertEquals(0, player.mediaItemCount)
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun play_fromTheMiddleQueuesTheWholeListInThePlayer_withTheIndexOnTheItem() = scope.runTest {
        val result = playback.play(PlaybackSource.ByAlbum(album.id), item(3))

        assertTrue(result.isRight())
        assertEquals((1L..5L).map(::item), playback.queue.first().map { it.id })
        assertEquals(2, playback.queueIndex.first())
        assertEquals(item(3), playback.currentItem.first()?.id)
        assertEquals(5, player.mediaItemCount)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(uriOf(3), player.currentMediaItem?.localConfiguration?.uri)
    }

    @Test
    fun play_usesNamesAndTheDurationOfTheSong() = scope.runTest {
        playback.play(PlaybackSource.AllSongs, item(2))

        val item = playback.currentItem.first()
        assertEquals("Artist", item?.artist)
        assertEquals("Album", item?.album)
        assertEquals(12.seconds, item?.duration)
    }

    @Test
    fun play_followsTheOrderOfTheSourceItWasSelectedFrom() = scope.runTest {
        playback.play(PlaybackSource.ByArtist(artist.id), item(6))

        assertEquals((1..6).map { "Song $it" }, playback.queue.first().map { it.title })
        assertEquals(5, playback.queueIndex.first())
    }

    @Test
    fun play_startsAtTheStartAndReplacesWhatWasLoaded() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playback.seekTo(5.seconds)

        playback.play(PlaybackSource.ByAlbum(otherAlbum.id), item(6))

        assertEquals(listOf(item(6)), playback.queue.first().map { it.id })
        assertEquals(1, player.mediaItemCount)
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun play_anItemOutsideTheSourceFailsAndChangesNothing() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))

        val result = playback.play(PlaybackSource.ByAlbum(album.id), item(6))

        assertEquals(PlaybackError.ItemNotInSource(item(6), PlaybackSource.ByAlbum(album.id)).left(), result)
        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(5, player.mediaItemCount)
    }

    @Test
    fun play_anUnknownSourceFailsWithTheLibraryError() = scope.runTest {
        val result = playback.play(PlaybackSource.ByAlbum(AlbumId(99)), item(1))

        assertEquals(PlaybackError.Library(LibraryError.AlbumNotFound(AlbumId(99))).left(), result)
        assertNull(playback.currentItem.first())
    }

    @Test
    fun play_aReadErrorFailsWithTheLibraryError() = scope.runTest {
        library.setReadError(StorageError.Unavailable)

        val result = playback.play(PlaybackSource.AllSongs, item(1))

        assertEquals(PlaybackError.Library(LibraryError.Storage(StorageError.Unavailable)).left(), result)
        assertNull(playback.currentItem.first())
    }

    @Test
    fun play_startsPlaying_andThePositionAdvancesAsThePlayerPlays() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        assertTrue(playback.isPlaying.first())

        playTo(4.seconds)

        assertNear(4.seconds, playback.position.first())
    }

    @Test
    fun pause_stopsThePosition_andResumeGoesOnFromThere() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playTo(4.seconds)

        playback.pause()
        val paused = playback.position.first()
        idle()
        tick()

        assertFalse(playback.isPlaying.first())
        assertFalse(player.playWhenReady)
        assertEquals(paused, playback.position.first())

        playback.resume()
        playTo(paused + 2.seconds)

        assertTrue(playback.isPlaying.first())
        assertNear(paused + 2.seconds, playback.position.first())
    }

    @Test
    fun pauseAndResume_twiceAreTheSameAsOnce() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))

        playback.pause()
        playback.pause()
        assertFalse(playback.isPlaying.first())

        playback.resume()
        playback.resume()
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun seekTo_movesThePosition() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))

        playback.seekTo(6.seconds)

        assertEquals(6.seconds, playback.position.first())
        assertEquals(6_000, player.currentPosition)
    }

    @Test
    fun seekTo_clampsToTheStart() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playTo(2.seconds)

        playback.seekTo((-5).seconds)

        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun seekTo_clampsToTheDuration() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        ready()
        playback.pause()
        idle()

        playback.seekTo(1_000.seconds)

        assertEquals(player.duration.milliseconds, playback.position.first())
        assertEquals(item(1), playback.currentItem.first()?.id)
    }

    @Test
    fun next_inTheMiddleMovesOnAndStartsAtTheStart() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playback.seekTo(5.seconds)

        playback.next()

        assertEquals(item(4), playback.currentItem.first()?.id)
        assertEquals(3, playback.queueIndex.first())
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun next_onTheLastItemDoesNothing() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(5))
        playback.seekTo(5.seconds)

        playback.next()

        assertEquals(item(5), playback.currentItem.first()?.id)
        assertEquals(5.seconds, playback.position.first())
    }

    @Test
    fun aMediaButtonsNext_startsTheNextItem() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        val before = playback.startCount.first()

        player.seekToNext()
        tick()

        assertEquals(item(4), playback.currentItem.first()?.id)
        assertEquals(before + 1, playback.startCount.first())
    }

    @Test
    fun scrubbingTo0_doesNotStartTheItem() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playTo(2.seconds)
        val before = playback.startCount.first()

        playback.seekTo(Duration.ZERO)
        tick()

        assertEquals(before, playback.startCount.first())
    }

    @Test
    fun withoutTheApp_itPlaysOnOnlyWhilePlaying() = scope.runTest {
        assertFalse(player.playsOn())

        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        ready()
        assertTrue(player.playsOn())

        playback.pause()
        assertFalse(player.playsOn())
    }

    @Test
    fun previous_pastTheThresholdStartsTheItemAgain_andTheQueueIndexStays() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playTo(RestartThreshold + Tick)

        playback.previous()

        assertEquals(item(3), playback.currentItem.first()?.id)
        assertEquals(2, playback.queueIndex.first())
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun previous_atTheThresholdStartsThePreviousItem() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playback.pause()
        playback.seekTo(RestartThreshold)

        playback.previous()

        assertEquals(item(2), playback.currentItem.first()?.id)
        assertEquals(1, playback.queueIndex.first())
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun previous_atTheStartOfAnItemStartsThePreviousItem() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))

        playback.previous()

        assertEquals(item(2), playback.currentItem.first()?.id)
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun previous_twiceInARow_startsTheItemAgainAndThenThePreviousOne() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playback.seekTo(8.seconds)

        playback.previous()
        playTo(1.seconds)
        assertEquals(item(3), playback.currentItem.first()?.id)
        assertNear(1.seconds, playback.position.first())
        playback.previous()

        assertEquals(item(2), playback.currentItem.first()?.id)
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun previous_onAPausedItem_startsItAgainAndThenThePreviousOne_andBothStayPaused() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playback.seekTo(8.seconds)
        playback.pause()

        playback.previous()
        idle()
        assertEquals(item(3), playback.currentItem.first()?.id)
        assertEquals(Duration.ZERO, playback.position.first())
        assertFalse(playback.isPlaying.first())
        playback.previous()
        idle()

        assertEquals(item(2), playback.currentItem.first()?.id)
        assertEquals(Duration.ZERO, playback.position.first())
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun previous_onTheFirstItemStartsItAgain_pastTheThreshold() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playback.seekTo(8.seconds)

        playback.previous()

        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(0, playback.queueIndex.first())
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun previous_onTheFirstItemStartsItAgain_belowTheThreshold() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playTo(2.seconds)

        playback.previous()

        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun theStartCount_changesWithEveryStartOfAnItem_theSameOneIncluded() = scope.runTest {
        val before = playback.startCount.first()

        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        val played = playback.startCount.first()
        playback.next()
        val next = playback.startCount.first()
        playback.seekTo(8.seconds)
        playback.previous()
        val restarted = playback.startCount.first()
        playback.previous()
        val previous = playback.startCount.first()
        playUntilTheNextItem()
        val ended = playback.startCount.first()

        assertEquals(item(4), playback.currentItem.first()?.id)
        assertEquals(listOf(1, 2, 3, 4, 5), listOf(played, next, restarted, previous, ended).map { it - before })
    }

    @Test
    fun theStartCount_staysWhenNothingStarts() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(5))
        val started = playback.startCount.first()

        playback.next()
        playback.seekTo(5.seconds)
        playback.pause()
        playback.resume()
        playTo(7.seconds)

        assertEquals(started, playback.startCount.first())
    }

    @Test
    fun anItemThatEnds_isFollowedByTheNextFromItsStart_andItPlays() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))

        playUntilTheNextItem()

        assertEquals(item(2), playback.currentItem.first()?.id)
        assertEquals(1, playback.queueIndex.first())
        // It plays on while the test reads it, so it is only known to be near its start.
        assertTrue(playback.position.first() < 1.seconds)
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun theLastItemEnds_andNothingIsLoaded() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(4))

        TestPlayerRunHelper.run(player).untilState(Player.STATE_ENDED)
        tick()

        assertNull(playback.currentItem.first())
        assertNull(playback.queueIndex.first())
        assertEquals(emptyList<Any>(), playback.queue.first())
        assertEquals(Duration.ZERO, playback.position.first())
        assertFalse(playback.isPlaying.first())
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun aPausedItemNeverEnds() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playback.pause()
        playback.seekTo(10.seconds)

        idle()
        tick()

        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(10.seconds, playback.position.first())
    }

    @Test
    fun nextPreviousAndSeekTo_leaveAPausedItemPaused() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))
        playback.pause()

        playback.next()
        playback.previous()
        playback.seekTo(4.seconds)
        idle()
        tick()

        assertFalse(playback.isPlaying.first())
        assertEquals(4.seconds, playback.position.first())
    }

    @Test
    fun nextAndPrevious_leaveAPlayingItemPlaying() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(3))

        playback.next()
        playTo(2.seconds)

        assertTrue(playback.isPlaying.first())
        assertNear(2.seconds, playback.position.first())
    }

    @Test
    fun play_whileAnotherItemIsPausedStartsPlaying() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playback.pause()

        playback.play(PlaybackSource.ByAlbum(album.id), item(2))

        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun seeking_movesFasterThanPlaying_andTheItemPlaysOnFromThereWhenItStops() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(otherAlbum.id), item(6))

        playback.startSeeking(SeekDirection.Forward)
        hold(1.seconds)
        assertEquals(1.seconds * SEEK_SPEED, playback.position.first())
        assertTrue(playback.isPlaying.first())

        playback.stopSeeking()
        playTo(1.seconds * SEEK_SPEED + 1.seconds)

        assertTrue(player.playWhenReady)
        assertNear(1.seconds * SEEK_SPEED + 1.seconds, playback.position.first())
    }

    @Test
    fun seekingBackwards_stopsAtTheStart() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playback.seekTo(8.seconds)

        playback.startSeeking(SeekDirection.Backward)
        hold(0.5.seconds)
        assertEquals(3.seconds, playback.position.first())

        hold(1.seconds)
        assertEquals(Duration.ZERO, playback.position.first())
        assertEquals(item(1), playback.currentItem.first()?.id)
    }

    @Test
    fun seekingForwards_stopsAtTheEnd_andTheItemEndsOnlyOnceItIsReleased() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        ready()

        playback.startSeeking(SeekDirection.Forward)
        hold(5.seconds)
        idle()

        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(player.duration.milliseconds, playback.position.first())

        playback.stopSeeking()
        playUntilTheNextItem()

        assertEquals(item(2), playback.currentItem.first()?.id)
    }

    @Test
    fun seekingForwards_toTheEndOfTheLastItem_endsTheQueueOnceItIsReleased() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(5))
        ready()

        playback.startSeeking(SeekDirection.Forward)
        hold(5.seconds)
        idle()
        assertEquals(item(5), playback.currentItem.first()?.id)

        playback.stopSeeking()
        TestPlayerRunHelper.run(player).untilState(Player.STATE_ENDED)
        tick()

        assertNull(playback.currentItem.first())
    }

    @Test
    fun seeking_aPausedItemMovesIt_andItStaysPaused() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        playback.pause()

        playback.startSeeking(SeekDirection.Forward)
        hold(0.5.seconds)
        playback.stopSeeking()
        idle()
        tick()

        assertFalse(playback.isPlaying.first())
        assertEquals(5.seconds, playback.position.first())
    }

    @Test
    fun seeking_aPausedItemToItsEndLeavesItThere() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))
        ready()
        playback.pause()

        playback.startSeeking(SeekDirection.Forward)
        hold(5.seconds)
        playback.stopSeeking()
        idle()
        tick()

        assertEquals(item(1), playback.currentItem.first()?.id)
        assertEquals(player.duration.milliseconds, playback.position.first())
    }

    @Test
    fun pause_duringAHold_leavesTheItemPausedOnceItIsReleased() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), item(1))

        playback.startSeeking(SeekDirection.Forward)
        playback.pause()
        assertFalse(playback.isPlaying.first())
        playback.stopSeeking()

        assertFalse(player.playWhenReady)
    }

    @Test
    fun theVolume_isThePhonesMediaVolume_asAPercentage() = scope.runTest {
        volumeKnown()

        val device = player.deviceInfo
        assertEquals(
            (player.deviceVolume - device.minVolume) * 100 / (device.maxVolume - device.minVolume),
            playback.volume.first(),
        )
    }

    @Test
    fun changeVolume_movesThePhonesVolumeByItsSteps() = scope.runTest {
        volumeKnown()
        val before = player.deviceVolume

        playback.changeVolume(-2)
        volumeReported()

        assertEquals(before - 2, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(percentOf(before - 2), playback.volume.first())
    }

    @Test
    fun changeVolume_stopsAtBothEnds() = scope.runTest {
        volumeKnown()

        playback.changeVolume(500)
        volumeReported()
        assertEquals(player.deviceInfo.maxVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(100, playback.volume.first())

        playback.changeVolume(-500)
        volumeReported()
        assertEquals(player.deviceInfo.minVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(0, playback.volume.first())
    }

    @Test
    fun theVolume_survivesPlayingAnotherItem_andThePlayersOwnVolumeStaysAtFull() = scope.runTest {
        volumeKnown()
        playback.changeVolume(-3)
        volumeReported()
        val volume = playback.volume.first()

        playback.play(PlaybackSource.AllSongs, item(1))

        assertEquals(volume, playback.volume.first())
        assertEquals(1f, player.volume)
    }

    @Test
    fun theVolume_followsThePhonesVolumeKeys() = scope.runTest {
        volumeKnown()

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
        volumeReported()

        assertEquals(percentOf(2), playback.volume.first())
    }

    private fun percentOf(level: Int): Int {
        val device = player.deviceInfo
        return (level - device.minVolume) * 100 / (device.maxVolume - device.minVolume)
    }

    /**
     * Lets the player play the current item until [position], and then lets a [Tick] read it. The position is
     * where the item is, not how far to go from where it was: the player plays on its own thread, so it may
     * already have moved on from what a test last read.
     */
    private fun TestScope.playTo(position: Duration) {
        TestPlayerRunHelper.playUntilPosition(player, player.currentMediaItemIndex, position.inWholeMilliseconds)
        tick()
    }

    /** Lets the player play until the current item ends and the next one starts, and then lets a [Tick] read it. */
    private fun TestScope.playUntilTheNextItem() {
        TestPlayerRunHelper.run(
            player,
        ).untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        tick()
    }

    /** Holds ⏮ or ⏭ for [duration] of the test's clock, a tick at a time. */
    private fun TestScope.hold(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    /** Lets the player handle what it was told. */
    private fun idle() {
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)
    }

    /** Lets the player's events reach their listeners, and then one [Tick] of the test's clock, which reads it. */
    private fun TestScope.tick() {
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(Tick)
        runCurrent()
    }

    /** Lets the player read the phone's media volume, which it does on a thread of its own. */
    private fun TestScope.volumeKnown() {
        RobolectricUtil.runMainLooperUntil { player.deviceInfo.maxVolume > 0 }
        tick()
    }

    /**
     * Sends what the system sends when the music stream's volume changes, which Robolectric doesn't, and lets
     * the player read it and a [Tick] pass.
     */
    private fun TestScope.volumeReported() {
        val level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        context.sendBroadcast(Intent(VOLUME_CHANGED_ACTION))
        RobolectricUtil.runMainLooperUntil { player.deviceVolume == level }
        tick()
    }

    /** Lets the player load the current item, so it knows its duration. */
    private fun ready() {
        TestPlayerRunHelper.run(player).untilState(Player.STATE_READY)
    }

    /** A position the player reached by playing, which can be a frame past the one asked for. */
    private fun assertNear(expected: Duration, actual: Duration) {
        assertTrue("Expected $expected, was $actual", abs((actual - expected).inWholeMilliseconds) <= NEAR_MS)
    }

    private fun item(id: Long) = PlaybackItemId.Song(SongId(id))

    private fun uriOf(id: Long): Uri = Uri.fromFile(File(folder.root, "$id.mp3"))

    /** A song of silence of [length], written as an MP3 of library/02's fixtures. */
    private fun song(id: Long, album: Album, length: Duration): Song {
        val file = File(folder.root, "$id.mp3")
        val stream = Mp3Stream(durationMs = length.inWholeMilliseconds.toInt())
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, stream).bytes())
        return Song(
            id = SongId(id),
            albumId = album.id,
            artistId = artist.id,
            title = "Song $id",
            trackNumber = id.toInt(),
            duration = length,
            uri = uriOf(id).toString(),
        )
    }

    private companion object {
        const val NEAR_MS = 100

        /** What the system sends when the volume keys move a stream's volume. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
