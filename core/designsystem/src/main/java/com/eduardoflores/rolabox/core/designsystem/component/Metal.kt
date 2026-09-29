package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eduardoflores.rolabox.core.designsystem.R
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal

private const val HAIRLINE_SPACING_PX = 3f
private const val HAIRLINE_LIGHT_ALPHA = 0.16f
private const val HAIRLINE_DARK_ALPHA = 0.028f
private const val LCD_SECONDARY_ALPHA = 0.7f

private val PillShape = RoundedCornerShape(26.dp)

/** Brushed-aluminum body: a vertical gradient with 1px vertical hairlines every 3px. */
fun Modifier.brushedMetal(top: Color, bottom: Color): Modifier = this
    .background(Brush.verticalGradient(listOf(top, bottom)))
    .drawBehind {
        val light = Color.White.copy(alpha = HAIRLINE_LIGHT_ALPHA)
        val dark = Color.Black.copy(alpha = HAIRLINE_DARK_ALPHA)
        var x = 0f
        while (x < size.width) {
            drawRect(light, Offset(x, 0f), Size(1f, size.height))
            drawRect(dark, Offset(x + 1f, 0f), Size(1f, size.height))
            x += HAIRLINE_SPACING_PX
        }
    }

/** Orange primary action. Only one per screen. Ignores taps while [loading]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    val colors = RolaboxMetal.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(6.dp, PillShape, ambientColor = colors.accent, spotColor = colors.accent)
            .clip(PillShape)
            .background(Brush.verticalGradient(listOf(colors.accentTop, colors.accentBottom)))
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Raised metal key, used for secondary actions such as Continue with Google. */
@Composable
fun MetalKeyButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = RolaboxMetal.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(2.dp, PillShape)
            .clip(PillShape)
            .background(Brush.verticalGradient(listOf(colors.keyTop, colors.keyBottom)))
            .border(1.dp, colors.keyBorder, PillShape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A 30dp metal key inside a 44dp touch target. */
@Composable
fun BackKey(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .offset(x = (-8).dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .shadow(1.dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.keyTop, colors.keyBottom)))
                .border(1.dp, colors.keyBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = BackChevron,
                contentDescription = contentDescription,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A small monochrome display that reports the screen's state. */
@Composable
fun LcdStrip(left: String, modifier: Modifier = Modifier, right: String = "", error: Boolean = false) {
    val colors = RolaboxMetal.colors
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bezel)
            .padding(3.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.lcd)
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error) {
                    Text(
                        text = stringResource(R.string.ds_lcd_error),
                        style = mono,
                        color = colors.lcd,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.lcdInk)
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                }
                Text(left.uppercase(), style = mono, color = colors.lcdInk)
            }
            if (right.isNotEmpty()) {
                Text(right.uppercase(), style = mono, color = colors.lcdInk.copy(alpha = LCD_SECONDARY_ALPHA))
            }
        }
    }
}

/** "r[vinyl]labox". The vinyl only spins while audio plays. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, isPlaying: Boolean = false, fontSize: Int = 21) {
    val colors = RolaboxMetal.colors
    val style = TextStyle(
        color = colors.ink,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.04).em,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ds_wordmark_start), style = style)
        SpinningVinyl(
            isPlaying = isPlaying,
            modifier = Modifier.padding(start = 3.dp, end = 1.dp, top = 3.dp),
            size = (fontSize * VINYL_TO_TEXT_RATIO).dp,
        )
        Text(stringResource(R.string.ds_wordmark_end), style = style)
    }
}

private const val VINYL_TO_TEXT_RATIO = 0.72f

// A chevron pointing left. It's a small drawing, so it's built here instead of adding the icons library.
private val BackChevron = ImageVector.Builder(
    name = "BackChevron",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,
).path(
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(14.5f, 6f)
    lineTo(8.5f, 12f)
    lineTo(14.5f, 18f)
}.build()
