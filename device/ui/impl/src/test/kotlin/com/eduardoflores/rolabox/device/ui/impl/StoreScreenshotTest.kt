package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.component.Wordmark
import com.eduardoflores.rolabox.common.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import org.junit.Test

/** The main menu, the hero of the Play listing and the README (ADR-039), and Play's feature graphic (ADR-041). */
class StoreScreenshotTest : StoreScreenshot() {
    // Something plays, so Now Playing leads the menu and the record spins; the highlight is on Music.
    @Test
    fun mainMenu() = shoot(StoreSet.Play, "01-main-menu") {
        RolaboxTheme { MainMenuDevice() }
    }

    // 1024 × 500: the wordmark on light aluminum, beside the device as it is on a phone, in dark so the two metals
    // stand apart. Nothing but the wordmark is text, so it reads at any size Play shows it.
    @Test
    fun featureGraphic() = shootGraphic("feature-graphic", width = 1024, height = 500) {
        RolaboxTheme(darkTheme = false) {
            Row(
                Modifier.fillMaxSize().brushedMetal(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(WORDMARK_DENSITY)) { Wordmark() }
                // A Pixel 6's screen in dp, drawn at a density that fits it in the graphic's height.
                CompositionLocalProvider(LocalDensity provides Density(PHONE_DENSITY)) {
                    val shape = MaterialTheme.shapes.extraLarge
                    Box(Modifier.requiredSize(411.dp, 914.dp).shadow(16.dp, shape).clip(shape)) {
                        RolaboxTheme(darkTheme = true) { MainMenuDevice() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainMenuDevice() {
    DeviceScreen(
        title = stringResource(R.string.device_title),
        batteryLevel = 0.8f,
        onWheelEvent = {},
        record = DeviceRecord.Spinning,
    ) {
        MainMenuList(rows = MenuRow.entries.filter { it.built }, highlightedIndex = 1)
    }
}

// Pixels per dp. The wordmark's 21 sp letters come out about 125 px tall; the phone, 914 dp tall, about 440 px.
private const val WORDMARK_DENSITY = 6f
private const val PHONE_DENSITY = 0.48f
