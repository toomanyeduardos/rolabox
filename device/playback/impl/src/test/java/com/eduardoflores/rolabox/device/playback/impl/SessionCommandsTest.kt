package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ADR-028, rule 8: only Rolabox's own controller may set the shuffle and repeat modes and send the play order. A second
 * controller connects to the same session as [PlaybackService]'s, and finds the commands missing.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class SessionCommandsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controllers = mutableListOf<MediaController>()
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        player = buildPlayer(context)
        player.addListener(PlayOrderKeeper(player))
        session = MediaSession.Builder(context, player)
            .setCallback(SessionCallback(resumption = null, context.packageName))
            .build()
    }

    @After
    fun tearDown() {
        controllers.forEach(MediaController::release)
        session.release()
        player.release()
    }

    @Test
    fun aSecondController_isNotOfferedShuffleOrRepeat_norThePlayOrder() {
        val other = connect(own = false)

        assertFalse(other.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
        assertFalse(other.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
        assertFalse(other.isCommandAvailable(Player.COMMAND_SET_PLAYLIST_METADATA))
        // What it may still do, as the notification and the media buttons do.
        assertTrue(other.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(other.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
    }

    @Test
    fun aSecondController_cantChangeThem() {
        val other = connect(own = false)

        other.shuffleModeEnabled = true
        other.repeatMode = Player.REPEAT_MODE_ALL
        idle()

        assertFalse(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    @Test
    fun rolaboxsController_setsThem_andThePlayOrder() {
        val own = connect(own = true)

        own.setMediaItems((1..3).map(::mediaItem))
        own.shuffleModeEnabled = true
        own.repeatMode = Player.REPEAT_MODE_ALL
        own.setPlayOrder(listOf("song:3", "song:1", "song:2"))
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 3 && player.shuffleModeEnabled }
        idle()

        assertTrue(own.isCommandAvailable(Player.COMMAND_SET_PLAYLIST_METADATA))
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        val timeline = player.currentTimeline
        assertEquals(2, timeline.getFirstWindowIndex(true))
        assertEquals(0, timeline.getNextWindowIndex(2, Player.REPEAT_MODE_OFF, true))
        assertEquals(1, timeline.getNextWindowIndex(0, Player.REPEAT_MODE_OFF, true))
    }

    // Found on the emulator: playing the same list again, with the same IDs as the items already there, must leave the
    // new order once its items arrive, not the list's.
    @Test
    fun theSameListPlayedAgain_keepsTheNewOrder_afterItsItemsArrive() {
        val own = connect(own = true)
        own.setMediaItems((1..4).map(::mediaItem))
        own.shuffleModeEnabled = true
        own.setPlayOrder(listOf("song:2", "song:4", "song:1", "song:3"))
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 4 && player.shuffleModeEnabled }
        idle()

        own.setMediaItems((1..4).map(::mediaItem), 2, 0)
        own.setPlayOrder(listOf("song:3", "song:1", "song:4", "song:2"))
        idle()

        assertEquals(listOf(2, 0, 3, 1), shuffledOrder())
    }

    // Found on the emulator: ⏮ on the first song of a round, under Repeat All, goes to the round's last song, and the
    // singleton answers that with the order for the end of the round. The ⏮ must follow the order it was pressed in.
    @Test
    fun aSeekSentBeforeANewOrder_followsTheOrderBeforeIt() {
        val own = connect(own = true)
        own.setMediaItems((1..4).map(::mediaItem), 2, 0)
        own.shuffleModeEnabled = true
        own.repeatMode = Player.REPEAT_MODE_ALL
        own.setPlayOrder(listOf("song:3", "song:1", "song:4", "song:2"))
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 4 && player.repeatMode == Player.REPEAT_MODE_ALL }
        idle()

        own.seekToPreviousMediaItem()
        own.setPlayOrder(listOf("song:4", "song:1", "song:3", "song:2"))
        idle()

        // The last of the first order, song 2, and then the new order.
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(listOf(3, 0, 2, 1), shuffledOrder())
    }

    @Test
    fun aControllerOfAnotherApp_withTheHint_isStillNotRolaboxs() {
        val callback = SessionCallback(resumption = null, packageName = "another.app")
        val other = MediaSession.Builder(context, buildPlayer(context)).setId("other").setCallback(callback).build()
        try {
            val controller = connect(own = true, to = other)

            assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
        } finally {
            other.player.release()
            other.release()
        }
    }

    private fun connect(own: Boolean, to: MediaSession = session): MediaController {
        val hints = Bundle().apply { if (own) putBoolean(OWN_CONTROLLER, true) }
        val future = MediaController.Builder(context, to.token).setConnectionHints(hints).buildAsync()
        RobolectricUtil.runMainLooperUntil { future.isDone }
        return future.get().also(controllers::add)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun shuffledOrder(): List<Int> {
        val timeline = player.currentTimeline
        return generateSequence(timeline.getFirstWindowIndex(true)) { index ->
            timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true).takeIf { it != C.INDEX_UNSET }
        }.toList()
    }

    private fun mediaItem(number: Int) =
        MediaItem.Builder().setMediaId("song:$number").setUri("file:///songs/$number.mp3").build()
}
