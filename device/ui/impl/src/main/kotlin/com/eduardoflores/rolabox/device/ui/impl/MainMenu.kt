package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.ui.api.MainMenuKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The main menu's rows, in the order they are shown. This fixed list is what the product has
 * (ADR-020, rule 15): the menu takes no contributions, and hiding a row is a preference. Now Playing is
 * shown only while a song is loaded (ADR-018).
 */
private enum class MainMenuRow { NowPlaying, Music, Podcasts, Audiobooks, ShuffleSongs, Settings }

internal fun EntryProviderScope<NavKey>.mainMenuEntry(
    hasLoadedSong: StateFlow<Boolean>,
    onNowPlayingClick: () -> Unit,
    onMusicClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    entry<MainMenuKey> {
        MainMenu(
            hasLoadedSong = hasLoadedSong,
            onNowPlayingClick = onNowPlayingClick,
            onMusicClick = onMusicClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

/**
 * Turning moves the highlight, which stops at the first and last rows. Center on Now Playing reports
 * [onNowPlayingClick], on Music [onMusicClick] and on Settings [onSettingsClick]; the other rows do
 * nothing until their screens exist.
 *
 * The highlight is the row, not its place: when Now Playing appears above it, the highlight stays on the
 * row it was on. It is saved with the entry like every list's (ADR-018, rule 7).
 */
@Composable
private fun MainMenu(
    hasLoadedSong: StateFlow<Boolean>,
    onNowPlayingClick: () -> Unit,
    onMusicClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val nowPlayingShown by hasLoadedSong.collectAsStateWithLifecycle()
    val shown = MainMenuRow.entries.filter { it != MainMenuRow.NowPlaying || nowPlayingShown }
    var highlightedOrdinal by rememberSaveable { mutableIntStateOf(MainMenuRow.Music.ordinal) }
    // A highlighted row that is gone leaves the highlight on Music.
    val highlighted = MainMenuRow.entries[highlightedOrdinal].takeIf { it in shown } ?: MainMenuRow.Music
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> {
                val index = (shown.indexOf(highlighted) + event.steps).coerceIn(0, shown.lastIndex)
                highlightedOrdinal = shown[index].ordinal
            }

            WheelEvent.Center -> when (highlighted) {
                MainMenuRow.NowPlaying -> onNowPlayingClick()
                MainMenuRow.Music -> onMusicClick()
                MainMenuRow.Settings -> onSettingsClick()
                MainMenuRow.Podcasts, MainMenuRow.Audiobooks, MainMenuRow.ShuffleSongs -> Unit
            }

            else -> Unit
        }
    }
    MainMenuList(rows = shown, highlightedIndex = shown.indexOf(highlighted))
}

@Composable
private fun MainMenuList(rows: List<MainMenuRow>, highlightedIndex: Int) {
    val listRows = rows.map { row ->
        when (row) {
            MainMenuRow.NowPlaying -> DeviceListRow(stringResource(R.string.device_now_playing))
            MainMenuRow.Music -> DeviceListRow(stringResource(R.string.device_music), opensSubmenu = true)
            MainMenuRow.Podcasts -> DeviceListRow(stringResource(R.string.device_podcasts), opensSubmenu = true)
            MainMenuRow.Audiobooks -> DeviceListRow(stringResource(R.string.device_audiobooks), opensSubmenu = true)
            MainMenuRow.ShuffleSongs -> DeviceListRow(stringResource(R.string.device_shuffle_songs))
            MainMenuRow.Settings -> DeviceListRow(stringResource(R.string.device_settings), opensSubmenu = true)
        }
    }
    DeviceList(rows = listRows, highlightedIndex = highlightedIndex)
}

@PreviewLightDark
@Composable
private fun MainMenuPreview() {
    RolaboxTheme {
        DeviceScreen(title = stringResource(R.string.device_title), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(rows = MainMenuRow.entries - MainMenuRow.NowPlaying, highlightedIndex = 0)
        }
    }
}

/** Something is loaded, so Now Playing is the first row, and the highlight stayed on Music. */
@PreviewLightDark
@Composable
private fun MainMenuNowPlayingPreview() {
    RolaboxTheme {
        DeviceScreen(title = stringResource(R.string.device_title), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(rows = MainMenuRow.entries, highlightedIndex = 1)
        }
    }
}
