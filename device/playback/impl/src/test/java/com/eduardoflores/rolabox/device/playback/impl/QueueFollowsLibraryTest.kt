package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
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
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
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

/** The real player's queue while the library changes under it (ADR-023): songs that are deleted or moved. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class QueueFollowsLibraryTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artist = Artist(ArtistId(1), "Artist")
    private val album = Album(AlbumId(1), artist.id, "Album", 2000)
    private val library = FakeLibraryRepository()
    private val scope = TestScope()
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        library.artists = listOf(artist)
        library.albums = listOf(album)
        library.songs = (1..5).map(::song)
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
    fun aSongInTheQueueThatIsGone_leavesIt_andThePlayingOneGoesOn() = scope.runTest {
        playFromAlbum(1)
        val starts = playback.startCount.first()

        library.songs = library.songs.filterNot { it.id == SongId(3) }
        runCurrent()

        assertEquals(listOf(1L, 2L, 4L, 5L), playback.queue.first().map { it.songNumber })
        assertEquals(4, player.mediaItemCount)
        assertEquals(1L, playback.currentItem.first()?.songNumber)
        assertEquals(starts, playback.startCount.first())
    }

    @Test
    fun aSongBeforeThePlayingOneThatIsGone_keepsTheSongPlaying() = scope.runTest {
        playFromAlbum(3)

        library.songs = library.songs.filterNot { it.id == SongId(1) }
        runCurrent()

        assertEquals(3L, playback.currentItem.first()?.songNumber)
        assertEquals(1, playback.queueIndex.first())
    }

    @Test
    fun thePlayingSongGone_movesToTheNext() = scope.runTest {
        playFromAlbum(2)
        val starts = playback.startCount.first()

        library.songs = library.songs.filterNot { it.id == SongId(2) }
        runCurrent()

        assertEquals(3L, playback.currentItem.first()?.songNumber)
        assertEquals(starts + 1, playback.startCount.first())
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun thePlayingSongGoneWhilePaused_movesToTheNext_andStaysPaused() = scope.runTest {
        playFromAlbum(2)
        playback.pause()

        library.songs = library.songs.filterNot { it.id == SongId(2) }
        runCurrent()

        assertEquals(3L, playback.currentItem.first()?.songNumber)
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun thePlayingSongGone_withNoneAfterIt_stops() = scope.runTest {
        playFromAlbum(5)

        library.songs = library.songs.filterNot { it.id == SongId(5) }
        runCurrent()

        assertNull(playback.currentItem.first())
        assertEquals(0, player.mediaItemCount)
        assertFalse(playback.isPlaying.first())
    }

    @Test
    fun everySongGone_stops() = scope.runTest {
        playFromAlbum(1)

        library.songs = emptyList()
        runCurrent()

        assertNull(playback.currentItem.first())
        assertEquals(emptyList<Any>(), playback.queue.first())
    }

    @Test
    fun aFileGoneBeforeTheLibraryLearnsOfIt_isSkipped_andLeavesTheQueueWhenItDoes() = scope.runTest {
        File(folder.root, "1.mp3").delete()
        playFromAlbum(1)
        RobolectricUtil.runMainLooperUntil { player.currentMediaItemIndex == 1 }
        runCurrent()
        assertEquals(2L, playback.currentItem.first()?.songNumber)

        library.songs = library.songs.filterNot { it.id == SongId(1) }
        runCurrent()

        assertEquals(2L, playback.currentItem.first()?.songNumber)
        assertEquals(4, playback.queue.first().size)
        assertNull(player.playerError)
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun aQueuedSongThatMoved_isGivenItsNewUri() = scope.runTest {
        playFromAlbum(1)
        val moved = File(folder.root, "moved.mp3")
        File(folder.root, "2.mp3").copyTo(moved)

        library.songs =
            library.songs.map { if (it.id == SongId(2)) it.copy(uri = Uri.fromFile(moved).toString()) else it }
        runCurrent()

        assertEquals(Uri.fromFile(moved), player.getMediaItemAt(1).localConfiguration?.uri)
        assertEquals(1L, playback.currentItem.first()?.songNumber)
        assertEquals(5, playback.queue.first().size)
    }

    /** Plays the album from song [id], and lets the library's locations reach the player. */
    private suspend fun TestScope.playFromAlbum(id: Long) {
        playback.play(PlaybackSource.ByAlbum(album.id), PlaybackItemId.Song(SongId(id)))
        runCurrent()
    }

    private val PlaybackItem.songNumber: Long
        get() = (id as PlaybackItemId.Song).id.value

    private fun song(number: Int): Song {
        val file = File(folder.root, "$number.mp3")
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = 10_000)).bytes())
        return Song(
            id = SongId(number.toLong()),
            albumId = album.id,
            artistId = artist.id,
            title = "Song $number",
            trackNumber = number,
            duration = 10.seconds,
            uri = Uri.fromFile(file).toString(),
        )
    }
}
