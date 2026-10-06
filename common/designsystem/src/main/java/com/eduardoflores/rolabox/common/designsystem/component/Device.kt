package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType
import kotlin.math.roundToInt

private val BodyGutter = 14.dp
private val BodyTopGap = 8.dp
private val WheelGap = 24.dp
private val BezelWidth = 4.dp
private val BezelShape = RoundedCornerShape(14.dp)
private val ScreenShape = RoundedCornerShape(10.dp)
private val HeaderMinHeight = 28.dp
private val HeaderSlotWidth = 44.dp
private val HeaderPadding = 10.dp
private val BatteryWidth = 18.dp
private val BatteryHeight = 9.dp
private val BatteryStroke = 1.5.dp
private val BatteryInset = 1.dp
private val BatteryCorner = 2.dp
private val BatteryTipWidth = 2.dp
private val BatteryTipHeight = 4.dp
private val BatteryTipGap = 1.dp

/**
 * The device (ADR-018): the aluminum body, with the display on top and the [wheel] below it,
 * centered. The wheel keeps its size, and what it leaves is the display's. Put a [DeviceDisplay] in
 * [content] and give it `Modifier.weight(1f)`, so the display's height comes from this layout and not
 * from a number around its text (ADR-017 rule 3). The body is edge to edge and pads for the system bars.
 */
@Composable
fun DeviceBody(
    wheel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .brushedMetal()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = BodyGutter, end = BodyGutter, top = BodyTopGap),
            content = content,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = WheelGap), contentAlignment = Alignment.Center) { wheel() }
    }
}

/**
 * The display of the device: a dark frame around a white screen that stays white in light and dark.
 * The screen has a header, with the [title] in the center, the battery on the right and an optional
 * [record] on the left, and [content] fills the rest. [batteryLevel] runs from 0 to 1.
 *
 * [record] is for the spinning record while something is loaded. With none, its place stays empty so
 * the title stays centered. The header is as tall as its text needs, and grows with the font scale.
 */
@Composable
fun DeviceDisplay(
    title: String,
    batteryLevel: Float,
    modifier: Modifier = Modifier,
    record: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = RolaboxMetal.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(BezelShape)
            .background(colors.displayBezel)
            .padding(BezelWidth),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ScreenShape)
                .background(colors.displayScreen),
        ) {
            DeviceHeader(title, batteryLevel, record)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
internal fun DeviceHeader(title: String, batteryLevel: Float, record: (@Composable () -> Unit)?) {
    val colors = RolaboxMetal.colors
    Column(Modifier.fillMaxWidth().background(colors.displayHeader)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = HeaderMinHeight).padding(horizontal = HeaderPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(HeaderSlotWidth), contentAlignment = Alignment.CenterStart) { record?.invoke() }
            Text(
                text = title,
                style = RolaboxType.styles.displayTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(HeaderSlotWidth), contentAlignment = Alignment.CenterEnd) {
                DeviceBattery(batteryLevel)
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(colors.displayRule))
    }
}

/** The battery in the display's header: an outline, a fill as wide as [level] (0 to 1), and a tip. */
@Composable
private fun DeviceBattery(level: Float) {
    val ink = RolaboxType.styles.displayTitle.color
    val clamped = level.coerceIn(0f, 1f)
    val description = stringResource(R.string.ds_battery_level, (clamped * PERCENT).roundToInt())
    Canvas(
        Modifier
            .width(BatteryWidth + BatteryTipGap + BatteryTipWidth)
            .heightIn(min = BatteryHeight)
            .semantics { contentDescription = description },
    ) {
        val stroke = BatteryStroke.toPx()
        val body = Size(BatteryWidth.toPx(), BatteryHeight.toPx())
        val top = (size.height - body.height) / 2f
        drawRoundRect(
            color = ink,
            topLeft = Offset(stroke / 2f, top + stroke / 2f),
            size = Size(body.width - stroke, body.height - stroke),
            cornerRadius = CornerRadius(BatteryCorner.toPx()),
            style = Stroke(stroke),
        )
        val inset = stroke + BatteryInset.toPx()
        drawRoundRect(
            color = ink,
            topLeft = Offset(inset, top + inset),
            size = Size((body.width - 2f * inset) * clamped, body.height - 2f * inset),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(body.width + BatteryTipGap.toPx(), top + (body.height - BatteryTipHeight.toPx()) / 2f),
            size = Size(BatteryTipWidth.toPx(), BatteryTipHeight.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

private const val PERCENT = 100
private val DisplayPreviewMax = 160.dp

@Composable
private fun PreviewRows() {
    val styles = RolaboxType.styles
    Column(Modifier.padding(12.dp)) {
        listOf("Music", "Podcasts", "Audiobooks", "Settings").forEach { Text(it, style = styles.displayTitle) }
    }
}

@PreviewLightDark
@Composable
private fun DeviceBodyPreview() {
    RolaboxTheme {
        // A phone's height, so the display has what the wheel leaves. The preview has no screen to fill.
        Box(Modifier.heightIn(min = 760.dp)) {
            DeviceBody(wheel = { Wheel(onEvent = {}) }) {
                DeviceDisplay(title = "rolabox", batteryLevel = 0.7f, modifier = Modifier.weight(1f)) { PreviewRows() }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceDisplayPreview() {
    RolaboxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DeviceDisplay(title = "Music", batteryLevel = 1f, modifier = Modifier.heightIn(max = DisplayPreviewMax)) {
                PreviewRows()
            }
            DeviceDisplay(
                title = "Now Playing",
                batteryLevel = 0.15f,
                modifier = Modifier.heightIn(
                    max = DisplayPreviewMax,
                ),
                record = {
                    SpinningVinyl(isPlaying = false, size = 13.dp)
                },
            ) {
                PreviewRows()
            }
            DeviceDisplay(title = "Empty", batteryLevel = 0f, modifier = Modifier.heightIn(max = DisplayPreviewMax)) {
                PreviewRows()
            }
        }
    }
}
