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
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.testing.FakePlaybackLocalDataSource
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BLUE = 0xFF0000FF.toInt()

/**
 * The real player's item while its cover arrives (ADR-027): the notification and the lock screen load it again through
 * a new artwork URI, and the music doesn't stop.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class ArtworkFollowsPlaybackTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artist = Artist(ArtistId(1), "Artist")
    private val album = Album(AlbumId(1), artist.id, "Album", 2000, ArtworkRef(SongId(1)))
    private val library = FakeLibraryRepository()
    private val artwork = FakeArtworkRepository()
    private val scope = TestScope()
    private lateinit var player: ExoPlayer
    private lateinit var playback: MediaPlayback

    @Before
    fun setUp() {
        library.artists = listOf(artist)
        library.albums = listOf(album)
        library.songs = (1..3).map(::song)
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
                artwork,
                FakeUserDataRepository(),
                PlayOrders(Random(0)),
            )
    }

    @After
    fun tearDown() {
        player.release()
    }

    private val artworkUri: Uri? get() = player.currentMediaItem?.mediaMetadata?.artworkUri

    @Test
    fun aCoverThatArrivesForTheItemPlaying_givesItANewArtworkUri_withoutStoppingIt() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), PlaybackItemId.Song(SongId(2)))
        runCurrent()
        TestPlayerRunHelper.playUntilPosition(player, 1, 2_000)
        val before = artworkUri

        // Downloaded for its album.
        artwork.covers[ArtworkRef(SongId(2))] = FakeArtworkRepository.solid(BLUE).right()
        runCurrent()
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)

        assertNotEquals(before, artworkUri)
        assertEquals(ArtworkRef(SongId(2)), artworkUri?.let(::artworkRefOf))
        // The same item, from where it was: replacing the item didn't start it over.
        assertEquals(1, player.currentMediaItemIndex)
        assertTrue("At ${player.currentPosition} ms", player.currentPosition >= 2_000)
        TestPlayerRunHelper.playUntilPosition(player, 1, 3_000)
        assertEquals(1, player.currentMediaItemIndex)
    }

    @Test
    fun theCoverOfAnotherItem_changesNothing() = scope.runTest {
        playback.play(PlaybackSource.ByAlbum(album.id), PlaybackItemId.Song(SongId(2)))
        runCurrent()
        val before = artworkUri

        artwork.covers[ArtworkRef(SongId(3))] = FakeArtworkRepository.solid(BLUE).right()
        runCurrent()

        assertEquals(before, artworkUri)
    }

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
