package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import org.junit.Test

/** The main menu, the hero of the Play listing and the README (ADR-039). */
class StoreScreenshotTest : StoreScreenshot() {
    // Something plays, so Now Playing leads the menu and the record spins; the highlight is on Music.
    @Test
    fun mainMenu() = shoot(StoreSet.Play, "01-main-menu") {
        RolaboxTheme {
            DeviceScreen(
                title = stringResource(R.string.device_title),
                batteryLevel = 0.8f,
                onWheelEvent = {},
                record = DeviceRecord.Spinning,
            ) {
                MainMenuList(rows = MenuRow.entries.filter { it.built }, highlightedIndex = 1)
            }
        }
    }
}
