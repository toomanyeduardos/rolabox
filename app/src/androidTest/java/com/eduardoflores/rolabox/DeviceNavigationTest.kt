package com.eduardoflores.rolabox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ActivityScenario
import arrow.core.Either
import arrow.core.getOrElse
import com.eduardoflores.rolabox.common.designsystem.component.WHEEL_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The device as it is assembled, with the wheel as the only input (ADR-018): from the main menu down
 * to a list of songs and back with MENU, checking that each level comes back on the row the user left,
 * and playing a song, Now Playing and the previous and next buttons.
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
        val artists = library.observeArtists().rows()
        val artist = artists[ARTIST_INDEX]
        val albums = library.observeAlbumsByArtist(artist.id).rows()
        val songs = library.observeSongsByAlbum(albums[0].id).rows()

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
    fun aSongPlaysItsListAndOpensNowPlaying_nextAndPreviousMoveTheQueue_andMenuComesBackOnEveryRow() {
        val artists = library.observeArtists().rows()
        val artist = artists[ARTIST_INDEX]
        val albums = library.observeAlbumsByArtist(artist.id).rows()
        val songs = library.observeSongsByAlbum(albums[0].id).rows()

        // Nothing is loaded yet, so the main menu has no Now Playing row.
        assertNoRow("Now Playing")
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(ARTIST_INDEX)
        wheel().pressCenter()
        wheel().turn(1)
        wheel().pressCenter()
        wheel().turn(SONG_INDEX)
        assertHighlighted(songs[SONG_INDEX].title)

        // Center plays the album from that song, and Now Playing shows its place in the queue.
        wheel().pressCenter()
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 1, songs.size)).assertExists()

        // Next and previous load the neighbour at its place, and never move in the screen stack.
        wheel().pressNext()
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 2, songs.size)).assertExists()
        wheel().pressPrevious()
        wheel().pressPrevious()
        composeRule.onNodeWithText(positionOf(SONG_INDEX, songs.size)).assertExists()

        // MENU leaves Now Playing, and every level is on the row it was left on.
        wheel().pressMenu()
        assertHighlighted(songs[SONG_INDEX].title)
        wheel().pressMenu()
        assertHighlighted(albums[0].title)
        wheel().pressMenu()
        assertHighlighted(artist.name)
        wheel().pressMenu()
        assertHighlighted("Artists")
        wheel().pressMenu()

        // Something is loaded now: the row is at the top, and the highlight stayed on Music.
        assertHighlighted("Music")
        assertRow("Now Playing")
        wheel().turn(-1)
        assertHighlighted("Now Playing")
        wheel().pressCenter()
        composeRule.onNodeWithText(positionOf(SONG_INDEX, songs.size)).assertExists()
    }

    @Test
    fun theHighlights_surviveTheActivityBeingRecreated() {
        val artists = library.observeArtists().rows()
        val artist = artists[ARTIST_INDEX]
        val albums = library.observeAlbumsByArtist(artist.id).rows()
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

    /** The rows the library has. A library that fails is a failed test, not an empty list (ADR-007, rule 7). */
    private fun <T> Flow<Either<LibraryError, List<T>>>.rows(): List<T> =
        runBlocking { first().getOrElse { error -> throw AssertionError("The library failed: $error") } }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun positionOf(number: Int, count: Int) = "$number OF $count"

    private fun assertRow(text: String) {
        composeRule.onNode(hasText(text), useUnmergedTree = true).assertExists()
    }

    private fun assertNoRow(text: String) {
        composeRule.onNode(hasText(text), useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertHighlighted(text: String) {
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}

private const val ARTIST_INDEX = 2
private const val SONG_INDEX = 2
