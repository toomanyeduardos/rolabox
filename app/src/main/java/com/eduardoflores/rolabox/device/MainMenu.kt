package com.eduardoflores.rolabox.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.core.designsystem.component.DeviceList
import com.eduardoflores.rolabox.core.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.core.device.DeviceScreen
import com.eduardoflores.rolabox.core.device.HandleWheelEvents
import com.eduardoflores.rolabox.core.device.rememberListHighlight

/** The main menu's rows, in the order they are shown. */
private enum class MainMenuRow { Music, Podcasts, Audiobooks, ShuffleSongs, Settings }

internal fun EntryProviderScope<NavKey>.mainMenuEntry(onMusicClick: () -> Unit) {
    entry<MainMenuKey> { MainMenu(onMusicClick = onMusicClick) }
}

/**
 * Turning moves the highlight, which stops at the first and last rows. Center on Music reports
 * [onMusicClick]; the other rows do nothing until their screens exist.
 */
@Composable
private fun MainMenu(onMusicClick: () -> Unit) {
    val highlight = rememberListHighlight()
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, MainMenuRow.entries.size)
            WheelEvent.Center -> if (MainMenuRow.entries[highlight.index] == MainMenuRow.Music) onMusicClick()
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
        DeviceScreen(title = stringResource(R.string.app_name), batteryLevel = 0.7f, onWheelEvent = {}) {
            MainMenuList(highlightedIndex = 0)
        }
    }
}
