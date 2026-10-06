package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.rememberListHighlight
import com.eduardoflores.rolabox.device.ui.api.MainMenuKey

/**
 * The main menu's rows, in the order they are shown. This fixed list is what the product has
 * (ADR-020, rule 15): the menu takes no contributions, and hiding a row is a preference.
 */
private enum class MainMenuRow { Music, Podcasts, Audiobooks, ShuffleSongs, Settings }

internal fun EntryProviderScope<NavKey>.mainMenuEntry(onMusicClick: () -> Unit, onSettingsClick: () -> Unit) {
    entry<MainMenuKey> { MainMenu(onMusicClick = onMusicClick, onSettingsClick = onSettingsClick) }
}

/**
 * Turning moves the highlight, which stops at the first and last rows. Center on Music reports
 * [onMusicClick] and on Settings [onSettingsClick]; the other rows do nothing until their screens exist.
 */
@Composable
private fun MainMenu(onMusicClick: () -> Unit, onSettingsClick: () -> Unit) {
    val highlight = rememberListHighlight()
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, MainMenuRow.entries.size)

            WheelEvent.Center -> when (MainMenuRow.entries[highlight.index]) {
                MainMenuRow.Music -> onMusicClick()
                MainMenuRow.Settings -> onSettingsClick()
                MainMenuRow.Podcasts, MainMenuRow.Audiobooks, MainMenuRow.ShuffleSongs -> Unit
            }

            else -> Unit
        }
    }
    MainMenuList(highlightedIndex = highlight.index)
}

@Composable
private fun MainMenuList(highlightedIndex: Int) {
    val rows = MainMenuRow.entries.map { row ->
        when (row) {
            MainMenuRow.Music -> DeviceListRow(stringResource(R.string.device_music), opensSubmenu = true)
            MainMenuRow.Podcasts -> DeviceListRow(stringResource(R.string.device_podcasts), opensSubmenu = true)
            MainMenuRow.Audiobooks -> DeviceListRow(stringResource(R.string.device_audiobooks), opensSubmenu = true)
            MainMenuRow.ShuffleSongs -> DeviceListRow(stringResource(R.string.device_shuffle_songs))
            MainMenuRow.Settings -> DeviceListRow(stringResource(R.string.device_settings), opensSubmenu = true)
        }
    }
    DeviceList(rows = rows, highlightedIndex = highlightedIndex)
}

@PreviewLightDark
@Composable
private fun MainMenuPreview() {
    RolaboxTheme {
        DeviceScreen(title = stringResource(R.string.device_title), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(highlightedIndex = 0)
        }
    }
}
