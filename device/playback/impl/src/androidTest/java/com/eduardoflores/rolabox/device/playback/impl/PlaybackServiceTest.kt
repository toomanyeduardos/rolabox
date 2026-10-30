package com.eduardoflores.rolabox.device.playback.impl

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The service on a device (ADR-023): a `MediaController` connects to its session, as the device's playback and
 * the system's controls do, and what it asks for plays. An item carries its URI only in its request metadata,
 * as an item from another process does, so the session's callback is what gives the player its audio.
 *
 * The volume goes through [MediaPlayback] itself: a controller can't set the volume of a local player, and the
 * JVM tests, with no controller, can't see that.
 */
// ADR-005 rule 5 lets tests replace a dispatcher. This test has nothing to replace it with: Media3 runs on the
// device's real main thread here.
@Suppress("InjectDispatcher")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "playback-service-test.mp3")
    private lateinit var controller: MediaController

    @Before
    fun setUp() = runBlocking(Dispatchers.Main) {
        file.writeBytes(FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = 10_000)).bytes())
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) { controller.release() }
        file.delete()
    }

    @Test
    fun aControllerConnectsToTheService_andWhatItAsksForPlays() = runBlocking(Dispatchers.Main) {
        val item = MediaItem.Builder()
            .setMediaId("song")
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(file)).build())
            .build()

        controller.setMediaItem(item)
        controller.prepare()
        controller.play()

        withTimeout(10.seconds) {
            while (controller.currentPosition < PLAYED_MS) delay(POLL_MS)
        }
        assertEquals(Player.STATE_READY, controller.playbackState)
        assertTrue(controller.isPlaying)
        assertEquals("song", controller.currentMediaItem?.mediaId)
    }

    @Test
    fun theSessionsPlayer_restartsWithPreviousPastThreeSeconds() = runBlocking(Dispatchers.Main) {
        val item = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(file)).build())
            .build()

        controller.setMediaItem(item)
        controller.prepare()

        assertEquals(RestartThreshold.inWholeMilliseconds, controller.maxSeekToPreviousPosition)
    }

    @Test
    fun theWheelsVolume_movesThePhonesMediaVolume_andTheStateFollowsThroughTheSession() = runBlocking {
        val audio = context.getSystemService(AudioManager::class.java)
        val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (min + max) / 2, 0)
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val scope = CoroutineScope(SupervisorJob())
        val playback =
            MediaPlayback(
                FakeLibraryRepository(),
                SessionConnection(context, Dispatchers.Main.immediate),
                PhoneVolume(context),
                scope,
                TimeSource.Monotonic,
            )

        playback.changeVolume(-2)

        assertEquals(before - 2, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        withTimeout(10.seconds) {
            playback.volume.first { it == (before - 2 - min) * 100 / (max - min) }
        }
        scope.cancel()
    }

    private companion object {
        const val PLAYED_MS = 500L
        const val POLL_MS = 50L
    }
}
