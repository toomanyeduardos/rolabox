package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.ui.api.MainMenuKey

internal fun EntryProviderScope<NavKey>.mainMenuEntry(
    shownRows: List<MenuRow>,
    onNowPlayingClick: () -> Unit,
    onMusicClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    entry<MainMenuKey> {
        MainMenu(
            shown = shownRows,
            onNowPlayingClick = onNowPlayingClick,
            onMusicClick = onMusicClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

/**
 * Turning moves the highlight, which stops at the first and last of the [shown] rows. Center on Now
 * Playing reports [onNowPlayingClick], on Music [onMusicClick] and on Settings [onSettingsClick]; the
 * other rows do nothing until their screens exist.
 *
 * The highlight is the row, not its place: when Now Playing appears above it, the highlight stays on the
 * row it was on. It is saved with the entry like every list's (ADR-018, rule 7).
 */
@Composable
private fun MainMenu(
    shown: List<MenuRow>,
    onNowPlayingClick: () -> Unit,
    onMusicClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var highlightedOrdinal by rememberSaveable { mutableIntStateOf(MenuRow.Music.ordinal) }
    // A highlighted row that is gone, such as one just hidden, leaves the highlight on Music, or on the
    // first row when Music is hidden too. Settings is always shown, so there is one.
    val highlighted = MenuRow.entries[highlightedOrdinal].takeIf { it in shown }
        ?: MenuRow.Music.takeIf { it in shown }
        ?: shown.first()
    // The app's name is this screen's title, and no other's (ADR-018, rule 19).
    ScreenTitle(stringResource(R.string.device_title))
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> {
                val index = (shown.indexOf(highlighted) + event.steps).coerceIn(0, shown.lastIndex)
                highlightedOrdinal = shown[index].ordinal
            }

            WheelEvent.Center -> when (highlighted) {
                MenuRow.NowPlaying -> onNowPlayingClick()
                MenuRow.Music -> onMusicClick()
                MenuRow.Settings -> onSettingsClick()
                MenuRow.Podcasts, MenuRow.Audiobooks, MenuRow.ShuffleSongs -> Unit
            }

            else -> Unit
        }
    }
    MainMenuList(rows = shown, highlightedIndex = shown.indexOf(highlighted))
}

@Composable
private fun MainMenuList(rows: List<MenuRow>, highlightedIndex: Int) {
    val listRows = rows.map { row ->
        when (row) {
            MenuRow.NowPlaying -> DeviceListRow(stringResource(R.string.device_now_playing))
            MenuRow.Music -> DeviceListRow(stringResource(R.string.device_music), opensSubmenu = true)
            MenuRow.Podcasts -> DeviceListRow(stringResource(R.string.device_podcasts), opensSubmenu = true)
            MenuRow.Audiobooks -> DeviceListRow(stringResource(R.string.device_audiobooks), opensSubmenu = true)
            MenuRow.ShuffleSongs -> DeviceListRow(stringResource(R.string.device_shuffle_songs))
            MenuRow.Settings -> DeviceListRow(stringResource(R.string.device_settings), opensSubmenu = true)
        }
    }
    DeviceList(rows = listRows, highlightedIndex = highlightedIndex)
}

@PreviewLightDark
@Composable
private fun MainMenuPreview() {
    RolaboxTheme {
        DeviceScreen(title = stringResource(R.string.device_title), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(rows = MenuRow.entries - MenuRow.NowPlaying, highlightedIndex = 0)
        }
    }
}

/** Something is loaded, so Now Playing is the first row, and the highlight stayed on Music. */
@PreviewLightDark
@Composable
private fun MainMenuNowPlayingPreview() {
    RolaboxTheme {
        DeviceScreen(title = stringResource(R.string.device_title), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(rows = MenuRow.entries, highlightedIndex = 1)
        }
    }
}

/** Podcasts and Audiobooks were hidden in Settings, so the main menu doesn't show them. */
@PreviewLightDark
@Composable
private fun MainMenuHiddenRowsPreview() {
    RolaboxTheme {
        DeviceScreen(title = stringResource(R.string.device_title), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(rows = listOf(MenuRow.Music, MenuRow.ShuffleSongs, MenuRow.Settings), highlightedIndex = 0)
        }
    }
}
