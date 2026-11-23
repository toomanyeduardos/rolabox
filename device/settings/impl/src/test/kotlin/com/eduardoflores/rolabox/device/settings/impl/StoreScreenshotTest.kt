package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.Repeat
import com.eduardoflores.rolabox.common.userdata.api.Shuffle
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import org.junit.Test

/** Settings for the Play listing and the README (ADR-039). */
class StoreScreenshotTest : StoreScreenshot() {
    // The highlight on Theme, the setting the listing's light and dark pairs show off.
    @Test
    fun settings() = shoot(StoreSet.Play, "06-settings") {
        RolaboxTheme {
            DeviceScreen(title = "Settings", batteryLevel = 0.8f, onWheelEvent = {}) {
                Box(Modifier.fillMaxSize()) {
                    SettingsList(
                        state = SettingsUiState(
                            shuffle = Shuffle.SONGS,
                            repeat = Repeat.ALL,
                            clicker = Clicker.ON,
                            darkTheme = DarkThemeConfig.FOLLOW_SYSTEM,
                            onlineArtwork = true,
                            sections = listOf(SettingsRow(title = { "Account" }, opens = StoreKey)),
                        ),
                        highlightedIndex = 3,
                    )
                }
            }
        }
    }
}

private data object StoreKey : NavKey
