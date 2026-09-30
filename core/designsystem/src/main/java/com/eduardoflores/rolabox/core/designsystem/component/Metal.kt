package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.R
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxType

private const val LCD_SECONDARY_ALPHA = 0.7f
private const val FLAT_KEY_ALPHA = 0.05f

private val PillShape = RoundedCornerShape(26.dp)
private val BackKeyTarget = 48.dp
private val BackKeyInset = 9.dp // Half the gap around the 30dp key, so the key lines up with the content edge.

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
            CircularProgressIndicator(Modifier.size(22.dp), color = colors.onAccent, strokeWidth = 2.5.dp)
        } else {
            Text(text, style = RolaboxType.styles.buttonLabel)
        }
    }
}

/**
 * A metal key for an action that sits beside the primary one. Disabled, it goes flat, and [text] can
 * carry a status such as a countdown, which a screen reader reads as the button's label.
 */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = RolaboxMetal.colors
    val background = if (enabled) {
        Modifier
            .shadow(2.dp, PillShape)
            .clip(PillShape)
            .background(Brush.verticalGradient(listOf(colors.keyTop, colors.keyBottom)))
            .border(1.dp, colors.keyBorder, PillShape)
    } else {
        Modifier
            .clip(PillShape)
            .background(colors.ink.copy(alpha = FLAT_KEY_ALPHA))
            .border(1.dp, colors.rule, PillShape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = RolaboxType.styles.keyLabel.let { if (enabled) it else it.copy(color = colors.muted) })
    }
}

/** A 30dp metal key inside a 48dp touch target. */
@Composable
fun BackKey(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .size(BackKeyTarget)
            .offset(x = -BackKeyInset)
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

/** A small monochrome display that reports the screen's state: a [left] label and a [right] status. */
@Composable
fun LcdStatusBar(left: String, modifier: Modifier = Modifier, right: String = "", error: Boolean = false) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    val secondary = styles.lcd.copy(color = styles.lcd.color.copy(alpha = LCD_SECONDARY_ALPHA))
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
                        style = styles.lcdBadge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.lcdInk)
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                }
                Text(left.uppercase(), style = styles.lcd)
            }
            if (right.isNotEmpty()) {
                Text(right.uppercase(), style = secondary)
            }
        }
    }
}

/** "r[vinyl]labox". The vinyl only spins while audio plays. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, isPlaying: Boolean = false) {
    val style = RolaboxType.styles.wordmark
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ds_wordmark_start), style = style)
        SpinningVinyl(
            isPlaying = isPlaying,
            modifier = Modifier.padding(start = 3.dp, end = 1.dp, top = 3.dp),
            size = (style.fontSize.value * VINYL_TO_TEXT_RATIO).dp,
        )
        Text(stringResource(R.string.ds_wordmark_end), style = style)
    }
}

private const val VINYL_TO_TEXT_RATIO = 0.72f

/** The top of a screen: an optional back key, then the [Wordmark]. */
@Composable
fun WordmarkHeader(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(modifier.height(BackKeyTarget), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            BackKey(onClick = onBack, contentDescription = stringResource(R.string.ds_back))
        }
        Wordmark()
    }
}

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

@Composable
private fun PreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    RolaboxTheme {
        Column(
            Modifier.brushedMetal().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@PreviewLightDark
@Composable
private fun LcdStatusBarPreview() {
    PreviewSurface {
        LcdStatusBar(left = "Sign in", right = "Offline OK")
        LcdStatusBar(left = "Sign in")
        LcdStatusBar(left = "Wrong password", error = true)
    }
}

@PreviewLightDark
@Composable
private fun WordmarkHeaderPreview() {
    PreviewSurface {
        WordmarkHeader()
        WordmarkHeader(onBack = {})
    }
}

@PreviewLightDark
@Composable
private fun PrimaryButtonPreview() {
    PreviewSurface {
        PrimaryButton(text = "Sign in", onClick = {})
        PrimaryButton(text = "Sign in", onClick = {}, loading = true)
    }
}

@PreviewLightDark
@Composable
private fun SecondaryButtonPreview() {
    PreviewSurface {
        SecondaryButton(text = "Resend link", onClick = {})
        SecondaryButton(text = "Resend in 0:42", onClick = {}, enabled = false)
    }
}
