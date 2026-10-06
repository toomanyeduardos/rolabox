package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

private const val VIGNETTE_RADIUS = 0.65f
private const val SHEEN_START_X = 0.43f
private const val SHEEN_END_X = 0.57f

/** A gradient stop at [at], whose [color] has a different alpha in the light and the dark theme. */
private class Stop(val at: Float, val color: Color, val lightAlpha: Float, val darkAlpha: Float = lightAlpha) {
    fun pair(dark: Boolean) = at to color.copy(alpha = if (dark) darkAlpha else lightAlpha)
}

// A diagonal band about a third of the way down, and a faint one near the bottom. In the dark
// theme the white parts are much fainter.
private val SheenStops = listOf(
    Stop(0f, Color.Transparent, 0f),
    Stop(0.18f, Color.White, 0.10f, 0.035f),
    Stop(0.34f, Color.White, 0.42f, 0.147f),
    Stop(0.48f, Color.White, 0.08f, 0.028f),
    Stop(0.58f, Color.Transparent, 0f),
    Stop(0.78f, Color.Black, 0.06f, 0.10f),
    Stop(0.92f, Color.White, 0.14f, 0.049f),
    Stop(1f, Color.Black, 0.05f),
)
private val VignetteStops = listOf(
    Stop(0.6f, Color.Transparent, 0f),
    Stop(1f, Color.Black, 0.12f, 0.20f),
)

/**
 * Brushed-aluminum body: the theme's small texture tile repeated by a shader, so the tile is
 * decoded once and no full-screen bitmap is kept. A soft diagonal sheen and darker edges are drawn
 * over it. The light and dark textures come from [RolaboxMetal].
 */
// The spread copies two small arrays per draw, which is cheaper than building the gradients by hand.
@Suppress("SpreadOperator")
@Composable
fun Modifier.brushedMetal(): Modifier {
    val colors = RolaboxMetal.colors
    val tile = ImageBitmap.imageResource(colors.texture)
    val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    val sheen = SheenStops.map { it.pair(colors.isDark) }.toTypedArray()
    val vignette = VignetteStops.map { it.pair(colors.isDark) }.toTypedArray()
    return this
        .background(colorResource(colors.body))
        .background(brush)
        .drawBehind {
            drawRect(
                Brush.linearGradient(
                    *sheen,
                    start = Offset(size.width * SHEEN_START_X, 0f),
                    end = Offset(size.width * SHEEN_END_X, size.height),
                ),
            )
            drawRect(Brush.radialGradient(*vignette, center = center, radius = size.maxDimension * VIGNETTE_RADIUS))
        }
}

@PreviewLightDark
@Composable
private fun BrushedMetalPreview() {
    RolaboxTheme { Box(Modifier.fillMaxSize().brushedMetal()) }
}
