package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.music.ui.api.MusicEntries
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import com.eduardoflores.rolabox.device.playback.ui.api.PlaybackEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsEntries
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The main menu on the device, composed with its screen stack: its rows follow the state while it is on
 * screen, and not only when it is pushed again.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceRouteMainMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val viewModel = FakeDeviceViewModel()

    // A queue restored at app start loads while the main menu is already showing.
    @Test
    fun somethingLoaded_whileTheMainMenuShows_addsNowPlaying_andTheHighlightStaysOnMusic() {
        composeRule.setContent {
            RolaboxTheme {
                DeviceRoute(
                    viewModel = viewModel,
                    musicEntries = NoMusicEntries,
                    playbackEntries = NoPlaybackEntries,
                    settingsEntries = NoSettingsEntries,
                    onOpenFullScreen = {},
                )
            }
        }
        composeRule.onNodeWithText("Now Playing").assertDoesNotExist()

        viewModel.hasLoadedSong.value = true

        composeRule.onNodeWithText("Now Playing").assertExists()
        composeRule.onNode(isSelected()).assert(hasAnyDescendant(hasText("Music")))
    }
}

private class FakeDeviceViewModel : DeviceViewModel() {
    override val hasLoadedSong = MutableStateFlow(false)
    override val queueStoppedUnplayable = MutableStateFlow(false)
    override val isPlaying = MutableStateFlow(false)
    override val mainMenuRows =
        MutableStateFlow(MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN })
    override val clicks = MutableStateFlow(false)
    override val playFailure = MutableStateFlow<PlayFailure?>(null)

    override fun onSongClick(source: PlaybackSource, songId: SongId, onPlaying: () -> Unit) = Unit
    override fun onShuffleSongsClick(onPlaying: () -> Unit) = Unit
    override fun onPlayFailureShown() = Unit
    override fun onNext() = Unit
    override fun onPrevious() = Unit
    override fun onPlayPause() = Unit
    override fun onSeekStart(direction: SeekDirection) = Unit
    override fun onSeekEnd() = Unit
}

private object NoMusicEntries : MusicEntries {
    override fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onArtistsClick: () -> Unit,
        onAlbumsClick: () -> Unit,
        onSongsClick: () -> Unit,
        onArtistClick: (artistId: Long) -> Unit,
        onAllSongsClick: (artistId: Long) -> Unit,
        onAlbumClick: (albumId: Long) -> Unit,
        onSongClick: (songId: Long) -> Unit,
        onGenresClick: () -> Unit,
        onGenreClick: (genreId: Long) -> Unit,
        onGenreArtistClick: (genreId: Long, artistId: Long) -> Unit,
        onGenreAllSongsClick: (genreId: Long, artistId: Long) -> Unit,
        onGenreAlbumClick: (genreId: Long, albumId: Long) -> Unit,
    ) = Unit
}

private object NoPlaybackEntries : PlaybackEntries {
    override fun screenStackEntries(scope: EntryProviderScope<NavKey>) = Unit
}

private object NoSettingsEntries : SettingsEntries {
    override fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onMainMenuClick: () -> Unit,
        onAboutClick: () -> Unit,
        onOpenFullScreen: (NavKey) -> Unit,
    ) = Unit

    override fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit) = Unit
}
