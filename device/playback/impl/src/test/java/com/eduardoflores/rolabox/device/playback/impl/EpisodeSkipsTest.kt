package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import java.io.File
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
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

/**
 * The session's player (ADR-031, rule 11): what the notification, the lock screen and a headset send. Previous and next
 * skip by seconds while an episode is loaded, and are offered for it, and a song is left as the player plays it.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class EpisodeSkipsTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: EpisodeSkips

    @Before
    fun setUp() {
        exoPlayer = buildPlayer(context) { setClock(FakeClock(true)) }
        player = EpisodeSkips(exoPlayer)
    }

    @After
    fun tearDown() {
        exoPlayer.release()
    }

    @Test
    fun anEpisode_offersPreviousAndNext_thoughItIsAlone() {
        load(episode())

        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }

    @Test
    fun nextAndPrevious_skipByTheEpisodesIncrements() {
        load(episode())
        player.seekTo(START_MS)
        idle()

        player.seekToNext()
        idle()
        assertNear(START_MS + SkipForward.inWholeMilliseconds, player.currentPosition)

        player.seekToPrevious()
        idle()
        assertNear(START_MS + SkipForward.inWholeMilliseconds - SkipBack.inWholeMilliseconds, player.currentPosition)

        player.seekToPreviousMediaItem()
        idle()
        assertNear(
            START_MS + SkipForward.inWholeMilliseconds - 2 * SkipBack.inWholeMilliseconds,
            player.currentPosition,
        )
    }

    @Test
    fun skipBack_andSkipForward_areTheSame15s() {
        // ADR-044: ⏭ then ⏮ comes back to the same place.
        assertEquals(15.seconds, SkipBack)
        assertEquals(SkipBack, SkipForward)
    }

    @Test
    fun songs_moveBetweenItems_asThePlayerDoes() {
        load(song(1), song(2))

        player.seekToNext()
        idle()

        assertEquals(1, player.currentMediaItemIndex)
        assertFalse(player.currentPosition > 1_000)
    }

    @Test
    fun aSongAlone_offersNoNext() {
        load(song(1))

        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
    }

    private fun load(vararg items: MediaItem) {
        player.setMediaItems(items.toList())
        player.prepare()
        TestPlayerRunHelper.run(exoPlayer).untilState(Player.STATE_READY)
    }

    private fun idle() {
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(exoPlayer)
    }

    private fun episode() = item(PlaybackItemId.Episode(EpisodeId(1)), "episode")

    private fun song(id: Long) = item(PlaybackItemId.Song(SongId(id)), "song-$id")

    private fun item(id: PlaybackItemId, name: String): MediaItem {
        val file = File(folder.root, "$name.mp3")
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = LENGTH_MS)).bytes())
        return MediaItem.Builder().setMediaId(id.mediaId).setUri(Uri.fromFile(file)).build()
    }

    private fun assertNear(expected: Long, actual: Long) {
        assertTrue("Expected $expected, was $actual", abs(actual - expected) <= NEAR_MS)
    }

    private companion object {
        const val LENGTH_MS = 45_000
        const val START_MS = 20_000L
        const val NEAR_MS = 150
    }
}
