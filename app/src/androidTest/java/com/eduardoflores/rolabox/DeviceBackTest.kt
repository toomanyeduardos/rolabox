package com.eduardoflores.rolabox

import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import arrow.core.Either
import arrow.core.getOrElse
import com.eduardoflores.rolabox.common.designsystem.component.WHEEL_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The system's back on the device as it is assembled (ADR-018, rule 9): from any depth it leaves the
 * app, and it never pops the screen stack or accepts scrub. The device is the first screen of the app
 * stack here, as it is after sign-in, so a back that nothing handles reaches the activity.
 * MENU, the only way back inside the display, is in [DeviceNavigationTest], and back in Settings in
 * [SettingsNavigationTest].
 */
@HiltAndroidTest
class DeviceBackTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<HiltTestActivity>

    @Inject lateinit var deviceUiEntries: DeviceUiEntries

    @Inject lateinit var library: LibraryRepository

    @Inject lateinit var playbackState: PlaybackState

    @Before
    fun setUp() {
        hiltRule.inject()
        HiltTestActivity.content = {
            RolaboxTheme {
                val backStack = remember { mutableStateListOf<NavKey>(DeviceKey) }
                NavDisplay(
                    backStack = backStack,
                    // What :app does with back: it pops the app stack, and never the first screen.
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        deviceUiEntries.appStackEntries(this, onOpenFullScreen = {}, onBack = {})
                    },
                )
            }
        }
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun backOnADeepScreen_leavesTheApp_andDoesNotGoBackOneScreen() {
        val artist = library.observeArtists().rows()[ARTIST_INDEX]
        val albums = library.observeAlbumsByArtist(artist.id).rows()
        openTheAlbumsOfTheArtist()
        assertHighlighted(albums[0].title)

        Espresso.pressBackUnconditionally()

        // A back that the display had taken would have left the activity resumed, on the artists.
        assertTheAppWasLeft()
    }

    @Test
    fun backOnNowPlayingInScrub_leavesTheApp_andNothingIsSought() {
        scrubOnNowPlaying()

        Espresso.pressBackUnconditionally()

        assertTheAppWasLeft()
        assertNothingIsSoughtAfterScrubsWait()
    }

    @Test
    fun theAppGoingToTheBackgroundInScrub_seeksNothing_andNowPlayingIsStillOnTop() {
        val songs = songsOfTheFirstAlbum()
        scrubOnNowPlaying()

        // Stopped and not destroyed, as after Home, so the screen's ViewModel and its wait are alive.
        scenario.moveToState(Lifecycle.State.CREATED)

        assertNothingIsSoughtAfterScrubsWait()
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 1, songs.size)).assertExists()
    }

    private fun openTheAlbumsOfTheArtist() {
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(ARTIST_INDEX)
        wheel().pressCenter()
        wheel().turn(1)
    }

    /** Plays a song, selects scrub and moves the marker away from 0:00, where the song is. */
    private fun scrubOnNowPlaying() {
        val songs = songsOfTheFirstAlbum()
        openTheAlbumsOfTheArtist()
        wheel().pressCenter()
        wheel().turn(SONG_INDEX)
        wheel().pressCenter()
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 1, songs.size)).assertExists()
        wheel().pressCenter()
        wheel().turn(SCRUB_STEPS)
        composeRule.waitForIdle()
        assertEquals(Duration.ZERO, position())
    }

    private fun songsOfTheFirstAlbum() = library.observeArtists().rows()[ARTIST_INDEX]
        .let { artist -> library.observeAlbumsByArtist(artist.id).rows() }
        .let { albums -> library.observeSongsByAlbum(albums[0].id).rows() }

    /** The activity is stopped or gone. Polled, since the system does it after back returns. */
    private fun assertTheAppWasLeft() {
        val deadline = SystemClock.uptimeMillis() + LEAVE_TIMEOUT_MILLIS
        while (scenario.state.isAtLeast(Lifecycle.State.STARTED) && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS)
        }
        assertFalse("The activity is still shown", scenario.state.isAtLeast(Lifecycle.State.STARTED))
    }

    /** The in-memory player only moves when it is sought, so the song is still at 0:00 after scrub's 3 s. */
    private fun assertNothingIsSoughtAfterScrubsWait() {
        Thread.sleep(AFTER_SCRUB_IDLE_MILLIS)
        assertEquals(Duration.ZERO, position())
    }

    private fun position() = runBlocking { playbackState.position.first() }

    /** The rows the library has. A library that fails is a failed test, not an empty list (ADR-007, rule 7). */
    private fun <T> Flow<Either<LibraryError, List<T>>>.rows(): List<T> =
        runBlocking { first().getOrElse { error -> throw AssertionError("The library failed: $error") } }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun positionOf(number: Int, count: Int) = "$number OF $count"

    private fun assertHighlighted(text: String) {
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}

private const val ARTIST_INDEX = 2
private const val SONG_INDEX = 1
private const val SCRUB_STEPS = 5
private const val LEAVE_TIMEOUT_MILLIS = 5_000L
private const val POLL_MILLIS = 50L

// Longer than scrub's wait of 3 s (ADR-018, rule 16), which is internal to the playback screens.
private const val AFTER_SCRUB_IDLE_MILLIS = 3_500L
