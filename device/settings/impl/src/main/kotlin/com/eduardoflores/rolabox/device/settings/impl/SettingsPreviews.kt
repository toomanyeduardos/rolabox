package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.settings.api.SettingsRow

// The screens as the designs show them (player-designs/device, frames 11 to 13), on the assembled
// device. They call the stateless composables with explicit state (ADR-016, rule 3).

@Composable
private fun DevicePreview(title: String, content: @Composable () -> Unit) {
    RolaboxTheme {
        DeviceScreen(title = title, batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsPreview() = DevicePreview(title = "Settings") {
    SettingsList(
        state = SettingsUiState(
            clicker = Clicker.ON,
            darkTheme = DarkThemeConfig.FOLLOW_SYSTEM,
            sections = listOf(SettingsRow(title = { "Account" }, opens = PreviewKey)),
        ),
        highlightedIndex = 0,
    )
}

@PreviewLightDark
@Composable
private fun MainMenuSettingsPreview() = DevicePreview(title = "Main Menu") {
    MainMenuSettingsList(
        rows = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN } +
            (MainMenuRow.AUDIOBOOKS to MainMenuRowVisibility.HIDDEN),
        highlightedIndex = MainMenuRow.AUDIOBOOKS.ordinal,
    )
}

@PreviewLightDark
@Composable
private fun AboutPreview() = DevicePreview(title = "About") {
    AboutList(AboutUiState(version = "1.0", songs = SongCount.Loaded(PREVIEW_SONG_COUNT)))
}

private data object PreviewKey : NavKey

// The number of songs frame 13 shows.
private const val PREVIEW_SONG_COUNT = 42
