package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
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
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import com.eduardoflores.rolabox.device.playback.testing.FakePlaybackLocalDataSource
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What is stored as the real player plays, and what a new process makes of it (ADR-023). */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class ResumeWhereYouLeftOffTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artist = Artist(ArtistId(1), "Artist")
    private val album = Album(AlbumId(1), artist.id, "Album", 2000)
    private val source = PlaybackSource.ByAlbum(album.id)
    private val library = FakeLibraryRepository()
    private val local = FakePlaybackLocalDataSource()
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
                local,
                scope.backgroundScope,
                scope.testScheduler.timeSource,
            )
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun nothingStored_loadsNothing() = scope.runTest {
        playback.restore()
        runCurrent()

        assertNull(playback.currentItem.first())
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun whatWasStored_isLoadedPausedAtItsPosition_withTheWholeList() = scope.runTest {
        local.point = point(song = 3, index = 2, position = 4.seconds)
        val starts = playback.startCount.first()

        playback.restore()
        runCurrent()

        assertEquals(songId(3), playback.currentItem.first()?.id)
        assertEquals((1..5).map(::songId), playback.queue.first().map { it.id })
        assertEquals(2, playback.queueIndex.first())
        assertEquals(4.seconds, playback.position.first())
        assertFalse(playback.isPlaying.first())
        assertFalse(player.playWhenReady)
        assertEquals(starts, playback.startCount.first())
    }

    @Test
    fun aStoredSongThatIsGone_loadsTheNextOneFromTheStart() = scope.runTest {
        library.songs = library.songs.filterNot { it.id == SongId(3) }
        local.point = point(song = 3, index = 2, position = 4.seconds)

        playback.restore()
        runCurrent()

        assertEquals(songId(4), playback.currentItem.first()?.id)
        assertEquals(Duration.ZERO, playback.position.first())
    }

    @Test
    fun aStoredLastSongThatIsGone_loadsNothing_andForgetsIt() = scope.runTest {
        library.songs = library.songs.filterNot { it.id == SongId(5) }
        local.point = point(song = 5, index = 4, position = 4.seconds)

        playback.restore()
        runCurrent()

        assertNull(playback.currentItem.first())
        assertNull(local.point)
    }

    @Test
    fun aStoredListThatIsGone_loadsNothing_andForgetsIt() = scope.runTest {
        library.albums = emptyList()
        library.songs = emptyList()
        local.point = point(song = 1, index = 0, position = 4.seconds)

        playback.restore()
        runCurrent()

        assertNull(playback.currentItem.first())
        assertNull(local.point)
    }

    @Test
    fun whatTheUserPlayed_isNotReplacedByWhatWasStored() = scope.runTest {
        playback.play(source, songId(1))
        runCurrent()
        local.point = point(song = 3, index = 2, position = 4.seconds)

        playback.restore()
        runCurrent()

        assertEquals(songId(1), playback.currentItem.first()?.id)
        assertTrue(playback.isPlaying.first())
    }

    @Test
    fun playingStoresTheListAndItem_andPauseStoresThePosition() = scope.runTest {
        playback.play(source, songId(2))
        runCurrent()
        assertEquals(source, local.point?.source)
        assertEquals(songId(2), local.point?.item)
        assertEquals(1, local.point?.index)

        playTo(2.seconds, index = 1)
        playback.pause()
        runCurrent()

        assertTrue(local.point!!.position >= StoredAtLeast)
    }

    @Test
    fun whilePlaying_itStoresEverySoOften() = scope.runTest {
        playback.play(source, songId(1))
        runCurrent()
        assertTrue(local.point!!.position < StoredAtLeast)
        playTo(2.seconds, index = 0)
        // Reaching the position pauses the player.
        playback.resume()

        advanceTimeBy(StoreEvery + 1.milliseconds)
        runCurrent()

        assertTrue(local.point!!.position >= StoredAtLeast)
    }

    @Test
    fun whenNothingIsLeftLoaded_nothingStaysStored() = scope.runTest {
        playback.play(source, songId(5))
        runCurrent()
        assertNotNull(local.point)

        library.songs = library.songs.filterNot { it.id == SongId(5) }
        runCurrent()

        assertNull(local.point)
    }

    @Test
    fun aResumption_givesWhatWasStored() = scope.runTest {
        local.point = point(song = 3, index = 2, position = 4.seconds)

        val resumed = playback.resumption()
        runCurrent()

        val items = resumed.get()
        assertEquals(5, items.mediaItems.size)
        assertEquals(2, items.startIndex)
        assertEquals(4_000, items.startPositionMs)
        assertNotNull(items.mediaItems[2].localConfiguration)
    }

    @Test
    fun aResumptionWithNothingStored_fails() = scope.runTest {
        val resumed = playback.resumption()
        runCurrent()

        assertTrue(resumed.isDone)
        assertTrue(runCatching { resumed.get() }.exceptionOrNull()?.cause is UnsupportedOperationException)
    }

    private fun playTo(position: Duration, index: Int) {
        TestPlayerRunHelper.playUntilPosition(player, index, position.inWholeMilliseconds)
    }

    private fun point(song: Int, index: Int, position: Duration) = ResumePoint(source, songId(song), index, position)

    private fun songId(number: Int) = PlaybackItemId.Song(SongId(number.toLong()))

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

/** The player stops a little around the position it is told to reach. */
private val StoredAtLeast = 1.5.seconds
