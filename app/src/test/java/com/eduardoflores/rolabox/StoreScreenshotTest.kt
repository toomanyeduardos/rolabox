package com.eduardoflores.rolabox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import org.junit.Test

/** Play's 512 × 512 icon, from the launcher icon's own layers (ADR-041 rule 2). */
class StoreScreenshotTest : StoreScreenshot() {
    // The full 108 dp canvas, edge to edge, as Android Studio's Play Store icon export draws it.
    // Play adds its own mask.
    @Test
    fun icon() = shootGraphic("icon-512", width = ICON_SIZE, height = ICON_SIZE) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

private const val ICON_SIZE = 512
