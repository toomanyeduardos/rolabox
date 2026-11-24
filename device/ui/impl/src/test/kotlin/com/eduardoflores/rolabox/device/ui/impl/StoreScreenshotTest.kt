package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.component.Wordmark
import com.eduardoflores.rolabox.common.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import kotlin.math.roundToInt
import org.junit.Test

/** The main menu, the hero of the Play listing and the README (ADR-039), and Play's feature graphic (ADR-041). */
class StoreScreenshotTest : StoreScreenshot() {
    // Something plays, so Now Playing leads the menu and the record spins; the highlight is on Music.
    @Test
    fun mainMenu() = shoot(StoreSet.Play, "01-main-menu") {
        RolaboxTheme { MainMenuDevice() }
    }

    // 1024 × 500: the wordmark on light aluminum, beside the device as it is on a phone, in dark so the two metals
    // stand apart. Nothing but the wordmark is text, so it reads at any size Play shows it. It renders at a high
    // density, so the wordmark is drawn large at its own size and stays sharp, and only the phone is scaled, down.
    @Test
    fun featureGraphic() = shootGraphic("feature-graphic", width = 1024, height = 500, dpi = GRAPHIC_DPI) {
        RolaboxTheme(darkTheme = false) {
            Row(
                Modifier.fillMaxSize().brushedMetal(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark()
                // A Pixel 6's screen in dp, laid out at its own size and drawn small enough to fit the graphic.
                val shape = MaterialTheme.shapes.extraLarge
                Box(Modifier.drawnAt(PHONE_SCALE).requiredSize(411.dp, 914.dp).shadow(16.dp, shape).clip(shape)) {
                    RolaboxTheme(darkTheme = true) { MainMenuDevice() }
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

/**
 * Lays the content out at its own size, then takes up and draws at [factor] times that size. The density and the
 * font scale stay the graphic's (ADR-017 rule 2); only the drawing is scaled. Use it to shrink: an enlarged vector
 * is drawn from a small bitmap and goes soft.
 */
private fun Modifier.drawnAt(factor: Float): Modifier = this
    .drawWithContent { scale(factor, pivot = Offset.Zero) { this@drawWithContent.drawContent() } }
    .layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout((placeable.width * factor).roundToInt(), (placeable.height * factor).roundToInt()) {
            placeable.place(0, 0)
        }
    }

// 6 px per dp, so the wordmark's 21 sp letters come out about 125 px tall. The phone, 914 dp or about 5,500 px tall,
// is drawn about 440 px tall.
private const val GRAPHIC_DPI = 960
private const val PHONE_SCALE = 0.08f
