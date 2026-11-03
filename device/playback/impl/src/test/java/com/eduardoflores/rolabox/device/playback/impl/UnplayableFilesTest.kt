package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.test.core.app.ApplicationProvider
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.AacStream
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.testing.FakePlaybackLocalDataSource
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Songs that can't be played (playback/04): a real player given library/02's broken kinds of file. A bad file never
 * stops the queue while something after it can play, and [MediaPlayback.skipped] says what happened.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class UnplayableFilesTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artist = Artist(ArtistId(1), "Artist")
    private val album = Album(AlbumId(1), artist.id, "Album", 2000, ArtworkRef(SongId(1)))
    private val library = FakeLibraryRepository()
    private val scope = TestScope()
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        library.artists = listOf(artist)
        library.albums = listOf(album)
        player = buildPlayer(context) { setClock(FakeClock(true)) }
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
                FakePlaybackLocalDataSource(),
                scope.backgroundScope,
                scope.testScheduler.timeSource,
            )
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun aBadFile_isSkipped_theNextOnePlays_andTheStateSaysWhich() = scope.runTest {
        songs(GOOD, TEXT, GOOD)
        playFrom(2)
        untilPlaying(3)

        val skipped = playback.skipped.first()
        assertEquals(2L, skipped?.last?.number)
        assertEquals(PlaybackError.Unplayable.Unsupported, skipped?.error)
        assertEquals(1, skipped?.count)
        assertTrue(playback.isPlaying.first())
        assertNull(playback.unplayableQueue.first())
    }

    @Test
    fun aTruncatedFileWithoutItsIndex_isSkipped() = scope.runTest {
        songs(TRUNCATED_M4A, GOOD)
        playFrom(1)
        untilPlaying(2)

        assertEquals(1L, playback.skipped.first()?.last?.number)
    }

    @Test
    fun aFileThatIsGone_isSkippedAsMissing() = scope.runTest {
        songs(GOOD, GOOD)
        File(folder.root, "1").delete()
        playFrom(1)
        untilPlaying(2)

        assertEquals(PlaybackError.Unplayable.Missing, playback.skipped.first()?.error)
    }

    @Test
    fun theNotice_endsOnItsOwn() = scope.runTest {
        songs(TEXT, GOOD)
        playFrom(1)
        untilPlaying(2)
        assertEquals(1L, playback.skipped.first()?.last?.number)

        advanceTimeBy(SkipNotice + 1.seconds)

        assertNull(playback.skipped.first())
    }

    @Test
    fun badFilesInARow_areCountedAndTheFirstGoodOnePlays() = scope.runTest {
        songs(TEXT, TEXT, TEXT, GOOD)
        playFrom(1)
        untilPlaying(4)

        val skipped = playback.skipped.first()
        assertEquals(3L, skipped?.last?.number)
        assertEquals(3, skipped?.count)
    }

    @Test
    fun aBadFileReachedByPlayingOn_isNamedForItself_andTheOneBeforeItFinished() = scope.runTest {
        songs(GOOD, TEXT, GOOD)
        playFrom(1)
        untilPlaying(1)
        untilPlaying(3)

        val skipped = playback.skipped.first()
        assertEquals(2L, skipped?.last?.number)
        assertEquals(1, skipped?.count)
    }

    @Test
    fun everyFileBad_stops_andTheStateSaysSo() = scope.runTest {
        songs(TEXT, TEXT, TEXT)
        playFrom(1)
        untilStopped()

        assertNull(playback.currentItem.first())
        assertFalse(playback.isPlaying.first())
        assertEquals(0, player.mediaItemCount)
        val stopped = playback.unplayableQueue.first()
        assertEquals(3L, stopped?.last?.number)
        assertEquals(3, stopped?.count)
    }

    @Test
    fun theStop_outlastsTheNotice_andAnotherPlayClearsIt() = scope.runTest {
        songs(TEXT)
        playFrom(1)
        untilStopped()

        advanceTimeBy(SkipNotice + 1.seconds)
        assertEquals(1L, playback.unplayableQueue.first()?.last?.number)

        songs(GOOD)
        playFrom(1)
        untilPlaying(1)
        assertNull(playback.unplayableQueue.first())
    }

    @Test
    fun aQueueEndingOnABadFile_stops_andSaysSo() = scope.runTest {
        songs(GOOD, TEXT)
        playFrom(2)
        untilStopped()

        assertEquals(2L, playback.unplayableQueue.first()?.last?.number)
    }

    @Test
    fun aQueueThatEndsAsQueuesDo_saysNothing() = scope.runTest {
        songs(GOOD)
        playFrom(1)
        untilPlaying(1)
        playback.seekTo(1.seconds)
        untilStopped()

        assertNull(playback.unplayableQueue.first())
    }

    @Test
    fun previousOntoABadFile_goesOnBackward() = scope.runTest {
        songs(GOOD, TEXT, GOOD)
        playFrom(3)
        untilPlaying(3)

        playback.previous()
        untilPlaying(1)

        assertEquals(2L, playback.skipped.first()?.last?.number)
    }

    @Test
    fun previousOntoABadFirstFile_comesBackForward() = scope.runTest {
        songs(TEXT, GOOD)
        playFrom(2)
        untilPlaying(2)

        playback.previous()
        // It lands on the bad file, fails there, and comes back.
        RobolectricUtil.runMainLooperUntil {
            player.currentMediaItemIndex == 1 &&
                player.playbackState == Player.STATE_READY
        }
        runCurrent()

        assertEquals(1L, playback.skipped.first()?.last?.number)
        assertEquals(2L, playback.currentItem.first()?.number)
    }

    @Test
    fun aBadFileWhilePaused_isSkipped_andStaysPaused() = scope.runTest {
        songs(GOOD, TEXT, GOOD)
        playFrom(1)
        untilPlaying(1)
        playback.pause()

        playback.next()
        RobolectricUtil.runMainLooperUntil { player.currentMediaItemIndex == 2 }
        runCurrent()

        assertEquals(3L, playback.currentItem.first()?.number)
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun theCodes_areTheKindsTheUserCanTellApart() {
        val kinds = mapOf(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND to PlaybackError.Unplayable.Missing,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED to PlaybackError.Unplayable.Damaged,
            PlaybackException.ERROR_CODE_DECODING_FAILED to PlaybackError.Unplayable.Damaged,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED to PlaybackError.Unplayable.Unsupported,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to PlaybackError.Unplayable.Unsupported,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED to PlaybackError.Unplayable.Protected,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION to PlaybackError.Unplayable.Protected,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION to PlaybackError.Unplayable.Unreadable,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED to PlaybackError.Unplayable.EngineFailed,
        )

        kinds.forEach { (code, kind) -> assertEquals(kind, PlaybackException("", null, code).toUnplayable()) }
    }

    private suspend fun playFrom(number: Long) {
        playback.play(PlaybackSource.ByAlbum(album.id), PlaybackItemId.Song(SongId(number)))
    }

    /** Lets the player run until it plays song [number], and the state has caught up with it. */
    private fun TestScope.untilPlaying(number: Long) {
        RobolectricUtil.runMainLooperUntil {
            player.playbackState == Player.STATE_READY && player.currentMediaItemIndex == (number - 1).toInt()
        }
        runCurrent()
    }

    private fun TestScope.untilStopped() {
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 0 }
        runCurrent()
    }

    private val PlaybackItem.number: Long
        get() = (id as PlaybackItemId.Song).id.value

    private fun songs(vararg contents: FixtureContent) {
        library.songs = contents.mapIndexed { index, content ->
            val number = index + 1
            val file = File(folder.root, "$number").apply { writeBytes(content.bytes()) }
            Song(
                id = SongId(number.toLong()),
                albumId = album.id,
                artistId = artist.id,
                title = "Song $number",
                trackNumber = number,
                duration = 1.seconds,
                uri = Uri.fromFile(file).toString(),
            )
        }
    }

    private companion object {
        val GOOD = FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = 1_000))
        val TEXT = FixtureContent.Text("This is a text file with an .mp3 extension.\n")
        val TRUNCATED_M4A = FixtureContent.Truncated(
            FixtureContent.M4a(tags = null, stream = AacStream(moovFirst = false)),
            fraction = 0.5,
        )
    }
}
