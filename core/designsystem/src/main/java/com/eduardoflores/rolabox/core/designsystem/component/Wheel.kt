package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxType
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelGestureRecognizer
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelHaptic
import com.eduardoflores.rolabox.core.designsystem.wheel.haptic
import com.eduardoflores.rolabox.core.designsystem.wheel.tickCount
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// the Wheel itself doesnt change size on larger or smaller fonts.
// this is to simulate the actual physical wheel of some old music players
private val WheelDiameter = 268.dp
private val CenterDiameter = 98.dp
private val LabelInset = 24.dp
private val SideIconInset = 26.dp
private val GlyphHeight = 12.dp
private val TriangleWidth = 8.dp
private val BarWidth = 2.dp
private val PlayBarWidth = 2.5.dp
private val PlayGap = 3.dp
private const val CENTER_HIGHLIGHT_ALPHA = 0.18f
private const val CENTER_HIGHLIGHT_ALPHA_DARK = 0.06f
private const val CENTER_HIGHLIGHT_RADIUS = 0.7f
private const val CENTER_HIGHLIGHT_Y = 0.7f

/**
 * The click wheel (ADR-018): a ring with the MENU, ⏮, ⏭ and ⏯ buttons and a center button, in the
 * metal materials of the theme. It reports what the user did through [onEvent] and knows nothing
 * about screens. Turning the ring reports [WheelEvent.Turn] with acceleration applied, a short press
 * reports the button under the finger, and MENU, ⏮ and ⏭ also report a hold: MENU's once, and ⏮ and ⏭
 * from the moment it is recognized until the finger lifts. A turn gets a light tick per step and a
 * press a stronger one, on devices that can vibrate.
 *
 * The MENU label and the icons keep their size at any font scale, because the wheel's shape is fixed
 * (ADR-018, ADR-017 rule 2 exception).
 */
// ADR-017 rule 3, rule 10: the wheel has a fixed size, and its MENU label sits inside it.
@Suppress("FixedHeightAroundText")
@Composable
fun Wheel(onEvent: (WheelEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val currentOnEvent by rememberUpdatedState(onEvent)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val holdTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    val emit = remember(haptics, scope) {
        { event: WheelEvent ->
            val ticks = event.tickCount()
            if (ticks > 0) {
                // Spaced out, since the system folds ticks that arrive together into one.
                scope.launch {
                    repeat(ticks) {
                        haptics.perform(WheelHaptic.Tick)
                        delay(TickSpacing)
                    }
                }
            } else {
                haptics.perform(event.haptic())
            }
            currentOnEvent(event)
        }
    }
    val texture = ImageBitmap.imageResource(colors.texture)
    val textureBrush = remember(texture) { ShaderBrush(ImageShader(texture, TileMode.Repeated, TileMode.Repeated)) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(WheelDiameter)
            .clip(CircleShape)
            .drawBehind { drawRing(colors.wheelRing, colors.wheelRingEdge) }
            .wheelGestures(holdTimeoutMillis, emit),
    ) {
        // ADR-017 rule 2 exception (rule 10): the label keeps its size at any font scale, as a part of a
        // control whose shape is fixed (ADR-018).
        @Suppress("ForbiddenMethodCall")
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Text(
                text = MENU_LABEL,
                style = RolaboxType.styles.wheelLabel,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = LabelInset),
            )
        }
        Canvas(Modifier.fillMaxSize()) { drawIcons(colors.wheelGlyph) }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(CenterDiameter)
                .drawBehind { drawCenterButton(textureBrush, colors.isDark) },
        )
    }
}

private val TickSpacing = 12.milliseconds
private const val MENU_LABEL = "MENU"

private fun HapticFeedback.perform(haptic: WheelHaptic?) {
    when (haptic) {
        WheelHaptic.Tick -> performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        WheelHaptic.Press -> performHapticFeedback(HapticFeedbackType.ContextClick)
        null -> Unit
    }
}

private fun Modifier.wheelGestures(holdTimeoutMillis: Long, emit: (WheelEvent) -> Unit): Modifier =
    pointerInput(holdTimeoutMillis) {
        val radius = size.toSize().width / 2f
        val recognizer = WheelGestureRecognizer(
            centerRadius = radius * CenterDiameter.value / WheelDiameter.value,
            ringRadius = radius,
        )
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            recognizer.down(down.position.x - radius, down.position.y - radius, down.uptimeMillis)
            try {
                var pressed = true
                var now = down.uptimeMillis
                while (pressed) {
                    val remaining = holdTimeoutMillis - (now - down.uptimeMillis)
                    val event = if (recognizer.canHold) {
                        withTimeoutOrNull(remaining.coerceAtLeast(0L)) { awaitPointerEvent() }
                    } else {
                        awaitPointerEvent()
                    }
                    if (event == null) {
                        recognizer.holdTimeout()?.let(emit)
                    } else {
                        val change = event.changes.first()
                        now = change.uptimeMillis
                        change.consume()
                        pressed = change.pressed && event.type != PointerEventType.Release
                        if (pressed) {
                            recognizer.move(change.position.x - radius, change.position.y - radius, change.uptimeMillis)
                                ?.let(emit)
                        } else {
                            recognizer.up()?.let(emit)
                        }
                    }
                }
            } finally {
                // A gesture that is cancelled while held still releases.
                recognizer.cancel()?.let(emit)
            }
        }
    }

private fun DrawScope.drawRing(ring: Color, edge: Color) {
    drawCircle(ring)
    drawCircle(edge, style = Stroke(width = 1.dp.toPx()), radius = size.minDimension / 2f - 0.5.dp.toPx())
}

private fun DrawScope.drawCenterButton(texture: ShaderBrush, dark: Boolean) {
    val highlight = if (dark) CENTER_HIGHLIGHT_ALPHA_DARK else CENTER_HIGHLIGHT_ALPHA
    drawCircle(texture)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = highlight),
            1f to Color.Transparent,
            center = Offset(center.x, size.height * CENTER_HIGHLIGHT_Y),
            radius = size.minDimension * CENTER_HIGHLIGHT_RADIUS,
        ),
    )
    val inner = if (dark) 0.3f else 0.12f
    val rim = if (dark) 0.15f else 0.06f
    drawCircle(
        Color.Black.copy(alpha = inner),
        style = Stroke(width = 2.dp.toPx()),
        radius =
            size.minDimension / 2f - 1.dp.toPx(),
    )
    drawCircle(
        Color.Black.copy(alpha = rim),
        style = Stroke(width = 1.dp.toPx()),
        radius =
            size.minDimension / 2f - 0.5.dp.toPx(),
    )
}

private fun DrawScope.drawIcons(color: Color) {
    val h = GlyphHeight.toPx()
    val tri = TriangleWidth.toPx()
    val bar = BarWidth.toPx()
    val midY = size.height / 2f

    // ⏮: a bar and two triangles pointing left, in from the left edge.
    var x = SideIconInset.toPx()
    drawRect(color, Offset(x, midY - h / 2f), Size(bar, h))
    x += bar
    repeat(2) {
        drawTriangle(color, x + tri, midY, h, -tri)
        x += tri
    }

    // ⏭: two triangles pointing right and a bar, in from the right edge.
    x = size.width - SideIconInset.toPx() - bar - 2 * tri
    repeat(2) {
        drawTriangle(color, x, midY, h, tri)
        x += tri
    }
    drawRect(color, Offset(x, midY - h / 2f), Size(bar, h))

    // ⏯: a triangle and two bars, in from the bottom edge.
    val playBar = PlayBarWidth.toPx()
    val gap = PlayGap.toPx()
    val width = tri + 2 * playBar + 2 * gap
    val top = size.height - LabelInset.toPx() - h
    var px = (size.width - width) / 2f
    drawTriangle(color, px, top + h / 2f, h, tri)
    px += tri + gap
    repeat(2) {
        drawRect(color, Offset(px, top), Size(playBar, h))
        px += playBar + gap
    }
}

/** A triangle of height [h] whose base is at [baseX] and whose tip is [width] away, so a negative width points left. */
private fun DrawScope.drawTriangle(color: Color, baseX: Float, midY: Float, h: Float, width: Float) {
    val path = Path().apply {
        moveTo(baseX, midY - h / 2f)
        lineTo(baseX + width, midY)
        lineTo(baseX, midY + h / 2f)
        close()
    }
    drawPath(path, color)
}

@PreviewLightDark
@Composable
private fun WheelPreview() {
    RolaboxTheme {
        Box(Modifier.brushedMetal().padding(24.dp)) { Wheel(onEvent = {}) }
    }
}
