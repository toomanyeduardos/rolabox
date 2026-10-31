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
import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The device as it is assembled, with the wheel as the only input (ADR-018): from the main menu down
 * to a list of songs and back with MENU, checking that each level comes back on the row the user left,
 * and playing a song, Now Playing and the playback buttons.
 * The library is the tests' own, so the rows are read from it and not repeated here. Playback is
 * [FakePlayback]: a test checks what the wheel asked of it, and then sets what a player would report.
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

    @Inject lateinit var playback: FakePlayback

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
        val path = path()
        val artist = path.artist
        val albums = path.albums
        val songs = path.songs

        // Main menu, then Music, then Artists.
        assertHighlighted("Music")
        wheel().pressCenter()
        assertHighlighted("Artists")
        wheel().pressCenter()

        // An artist, turned to, and its albums: "All Songs" first, then the first album.
        assertHighlighted(path.artists[0].name)
        wheel().turn(path.artistIndex)
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
        val path = path()
        val artist = path.artist
        val albums = path.albums
        val songs = path.songs

        // Nothing is loaded yet, so the main menu has no Now Playing row.
        assertNoRow("Now Playing")
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(path.artistIndex)
        wheel().pressCenter()
        wheel().turn(1)
        wheel().pressCenter()
        wheel().turn(SONG_INDEX)
        assertHighlighted(songs[SONG_INDEX].title)

        // Center plays the album from that song, and Now Playing shows its place in the queue.
        wheel().pressCenter()
        composeRule.waitForIdle()
        assertEquals(
            listOf(FakePlayback.PlayCall(PlaybackSource.ByAlbum(albums[0].id), itemOf(songs[SONG_INDEX]))),
            playback.playCalls,
        )
        playback.load(queueOf(songs), SONG_INDEX)
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 1, songs.size)).assertExists()

        // Next and previous ask for the neighbour, which shows at its place, and never move in the screen stack.
        wheel().pressNext()
        composeRule.waitForIdle()
        assertEquals(1, playback.nextCalls)
        playback.load(queueOf(songs), SONG_INDEX + 1)
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 2, songs.size)).assertExists()
        wheel().pressPrevious()
        wheel().pressPrevious()
        composeRule.waitForIdle()
        assertEquals(2, playback.previousCalls)
        playback.load(queueOf(songs), SONG_INDEX - 1)
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
    fun playPause_playsTheHighlightedSong_andPausesFromNowPlayingAndFromAListThatIsNotPlayable() {
        val path = path()
        val albums = path.albums
        val songs = path.songs

        // Nothing is loaded: ⏯ on the main menu only asks to resume, which plays nothing.
        wheel().pressPlayPause()
        composeRule.waitForIdle()
        assertEquals(1, playback.resumeCalls)
        assertNoRow("Now Playing")
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(path.artistIndex)
        wheel().pressCenter()
        wheel().turn(1)
        wheel().pressCenter()
        wheel().turn(SONG_INDEX)

        // On a song ⏯ does what center does: it plays the album from that song, and Now Playing is pushed.
        wheel().pressPlayPause()
        composeRule.waitForIdle()
        assertEquals(
            listOf(FakePlayback.PlayCall(PlaybackSource.ByAlbum(albums[0].id), itemOf(songs[SONG_INDEX]))),
            playback.playCalls,
        )
        playback.load(queueOf(songs), SONG_INDEX)
        composeRule.onNodeWithText(positionOf(SONG_INDEX + 1, songs.size)).assertExists()
        playback.positionAt(5.seconds)
        composeRule.onNodeWithText("0:05").assertExists()

        // On Now Playing ⏯ pauses what plays, and a second ⏯ plays what is paused.
        wheel().pressPlayPause()
        composeRule.waitForIdle()
        assertEquals(1, playback.pauseCalls)
        playback.playing(false)
        val resumes = playback.resumeCalls
        wheel().pressPlayPause()
        composeRule.waitForIdle()
        assertEquals(resumes + 1, playback.resumeCalls)
        playback.playing(true)

        // The albums are not playable rows, so ⏯ pauses there too, and nothing is pushed.
        wheel().pressMenu()
        wheel().pressMenu()
        wheel().pressPlayPause()
        assertHighlighted(albums[0].title)
        assertEquals(2, playback.pauseCalls)
        assertEquals(1, playback.playCalls.size)
        playback.playing(false)
        playback.positionAt(10.seconds)
        wheel().pressMenu()
        wheel().pressMenu()
        wheel().pressMenu()
        wheel().turn(-1)
        wheel().pressCenter()
        composeRule.onNodeWithText("0:10").assertExists()
    }

    @Test
    fun theQueueEndsOnNowPlaying_theDisplayGoesBackToTheListUnderIt_andTheMainMenuLosesItsRow() {
        val path = path()
        val songs = path.songs
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(path.artistIndex)
        wheel().pressCenter()
        wheel().turn(1)
        wheel().pressCenter()
        wheel().turn(songs.lastIndex)

        // The last song of the album plays, so the queue has nothing after it.
        wheel().pressCenter()
        composeRule.waitForIdle()
        playback.load(queueOf(songs), songs.lastIndex)
        composeRule.onNodeWithText(positionOf(songs.size, songs.size)).assertExists()

        // It ends: nothing is loaded, and Now Playing leaves the display for the list it was played from.
        playback.unload()
        composeRule.onNodeWithText(positionOf(songs.size, songs.size)).assertDoesNotExist()
        assertHighlighted(songs.last().title)

        // The main menu has no Now Playing row again.
        wheel().pressMenu()
        wheel().pressMenu()
        wheel().pressMenu()
        wheel().pressMenu()
        assertHighlighted("Music")
        assertNoRow("Now Playing")
    }

    @Test
    fun theHighlights_surviveTheActivityBeingRecreated() {
        val path = path()
        val artist = path.artist
        val albums = path.albums
        wheel().pressCenter()
        wheel().pressCenter()
        wheel().turn(path.artistIndex)
        wheel().pressCenter()
        wheel().turn(1)

        scenario.recreate()

        // The screen stack and the highlight are saved state of their entries, so the albums are
        // still on top, on the row they were on.
        assertHighlighted(albums[0].title)
        wheel().pressMenu()
        assertHighlighted(artist.name)
    }

    /**
     * The first artist whose first album has a song after the one at [SONG_INDEX], so a test can turn to
     * that song and go to the next. Which artist that is depends on the library, which is read here.
     */
    private fun path(): Path {
        val artists = library.observeArtists().rows()
        return artists.indices.firstNotNullOf { index ->
            val albums = library.observeAlbumsByArtist(artists[index].id).rows()
            val songs = albums.firstOrNull()?.let { library.observeSongsByAlbum(it.id).rows() }.orEmpty()
            Path(index, artists, albums, songs).takeIf { songs.size > SONG_INDEX + 1 }
        }
    }

    /** The rows the library has. A library that fails is a failed test, not an empty list (ADR-007, rule 7). */
    private fun <T> Flow<Either<LibraryError, List<T>>>.rows(): List<T> =
        runBlocking { first().getOrElse { error -> throw AssertionError("The library failed: $error") } }

    private fun itemOf(song: Song) = PlaybackItemId.Song(song.id)

    /** The queue a player loads when [songs] are played. */
    private fun queueOf(songs: List<Song>) =
        songs.map { PlaybackItem(itemOf(it), it.title, "Artist", "Album", it.duration) }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun positionOf(number: Int, count: Int) = "$number OF $count"

    private fun assertRow(text: String) {
        composeRule.onNode(hasText(text), useUnmergedTree = true).assertExists()
    }

    private fun assertNoRow(text: String) {
        composeRule.onNode(hasText(text), useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertHighlighted(name: String?) {
        val text = checkNotNull(name) { "The tests' library names everything" }
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}

/** The artists, the index of the one a test opens, its albums and the songs of its first album. */
private class Path(val artistIndex: Int, val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>) {
    val artist: Artist get() = artists[artistIndex]
}

private const val SONG_INDEX = 2
