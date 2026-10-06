package com.eduardoflores.rolabox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ActivityScenario
import com.eduardoflores.rolabox.common.designsystem.component.WHEEL_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The device as it is assembled, with the wheel as the only input (ADR-018): from the main menu down
 * to a list of songs and back with MENU, checking that each level comes back on the row the user left.
 * The library is the in-memory one, so the rows are read from it and not repeated here.
 */
@HiltAndroidTest
class DeviceNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<HiltTestActivity>

    @Inject lateinit var deviceUiEntries: DeviceUiEntries

    @Inject lateinit var library: LibraryRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        HiltTestActivity.content = {
            RolaboxTheme {
                val backStack = remember { mutableStateListOf<NavKey>(DeviceKey) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
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
    fun fromTheMainMenuDownToSongsAndBack_everyLevelComesBackOnItsRow() {
        val artists = runBlocking { library.observeArtists().first().getOrNull().orEmpty() }
        val artist = artists[ARTIST_INDEX]
        val albums = runBlocking { library.observeAlbumsByArtist(artist.id).first().getOrNull().orEmpty() }
        val songs = runBlocking { library.observeSongsByAlbum(albums[0].id).first().getOrNull().orEmpty() }

        // Main menu, then Music, then Artists.
        assertHighlighted("Music")
        wheel().pressCenter()
        assertHighlighted("Artists")
        wheel().pressCenter()

        // An artist, turned to, and its albums: "All Songs" first, then the first album.
        assertHighlighted(artists[0].name)
        wheel().turn(ARTIST_INDEX)
        assertHighlighted(artist.name)
        wheel().pressCenter()
        assertHighlighted("All Songs")
        wheel().turn(1)
        assertHighlighted(albums[0].title)
        wheel().pressCenter()

        // The songs of that album, turned to the third.
        assertHighlighted(songs[0].title)
        wheel().turn(SONG_INDEX)
        assertHighlighted(songs[SONG_INDEX].title)

        // And back up, one MENU at a time: each level is on the row it was left on.
        wheel().pressMenu()
        assertHighlighted(albums[0].title)
        wheel().pressMenu()
        assertHighlighted(artist.name)
        wheel().pressMenu()
        assertHighlighted("Artists")
        wheel().pressMenu()
        assertHighlighted("Music")
    }

    @Test
    fun theHighlights_surviveTheActivityBeingRecreated() {
        val artists = runBlocking { library.observeArtists().first().getOrNull().orEmpty() }
        val artist = artists[ARTIST_INDEX]
        val albums = runBlocking { library.observeAlbumsByArtist(artist.id).first().getOrNull().orEmpty() }
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(ARTIST_INDEX)
        wheel().pressCenter()
        wheel().turn(1)

        scenario.recreate()

        // The screen stack and the highlight are saved state of their entries, so the albums are
        // still on top, on the row they were on.
        assertHighlighted(albums[0].title)
        wheel().pressMenu()
        assertHighlighted(artist.name)
    }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun assertHighlighted(text: String) {
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}

private fun SemanticsNodeInteraction.pressCenter() {
    performTouchInput { click(center) }
}

private fun SemanticsNodeInteraction.pressMenu() {
    performTouchInput { click(pointOnRing(MENU_ANGLE_DEGREES)) }
}

/**
 * Turns the ring by [steps] steps, clockwise when positive: a finger that goes round it slowly enough
 * that the wheel applies no acceleration, so a step moves a row.
 */
private fun SemanticsNodeInteraction.turn(steps: Int) {
    performTouchInput {
        val direction = sign(steps.toFloat())
        val moves = abs(steps) * MOVES_PER_STEP + 1
        down(pointOnRing(START_ANGLE_DEGREES))
        repeat(moves) { move ->
            advanceEventTime(MOVE_INTERVAL_MILLIS)
            moveTo(pointOnRing(START_ANGLE_DEGREES + direction * (move + 1) * DEGREES_PER_MOVE))
        }
        up()
    }
}

private const val ARTIST_INDEX = 2
private const val SONG_INDEX = 2

// Degrees clockwise from 3 o'clock, as the wheel measures them: MENU is at the top.
private const val MENU_ANGLE_DEGREES = -90f
private const val START_ANGLE_DEGREES = 45f
private const val DEGREES_PER_MOVE = 5f
private const val MOVES_PER_STEP = 3
private const val MOVE_INTERVAL_MILLIS = 100L
private const val RING_POSITION = 0.75f

/** A point on the ring at [degrees], relative to the wheel being touched. */
private fun TouchInjectionScope.pointOnRing(degrees: Float): Offset {
    val radius = width / 2f * RING_POSITION
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}
