package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val NormalHeight = 6.dp
private val ScrubHeight = 8.dp
private val BarCorner = 4.dp
private val KnobSize = 12.dp
private val KnobCorner = 2.dp
private val LabelsGap = 7.dp
private const val KNOB_TURN_DEGREES = 45f
private const val PREVIEW_TIME = 0.33f
private const val PREVIEW_SCRUB = 0.49f
private const val PREVIEW_VOLUME = 0.62f
private val PreviewPadding = 16.dp

/** The three looks of a [DeviceProgressBar]. */
enum class ProgressBarMode {
    /** The time of the song: a thin bar with a dark fill. */
    Normal,

    /** Moving the position: a thicker bar with the accent fill and a knob at the end of it. */
    Scrub,

    /** The volume: the thin bar with the display's ink as the fill. */
    Volume,
}

/**
 * The bar of a device's display (ADR-018): a track filled up to [progress] (0 to 1), with [startLabel]
 * under its left end and [endLabel] under its right end, in tabular figures. The labels are the time
 * elapsed and the time left in [ProgressBarMode.Normal] and [ProgressBarMode.Scrub], and "VOL" and the
 * level in [ProgressBarMode.Volume]. [mode] sets the look. Put it in the content of a [DeviceDisplay].
 *
 * It shows state and takes no input: the wheel moves it. [description] is what a screen reader says
 * for it, such as "Playback position", and its range is read as [progress].
 */
@Composable
fun DeviceProgressBar(
    progress: Float,
    startLabel: String,
    endLabel: String,
    description: String,
    modifier: Modifier = Modifier,
    mode: ProgressBarMode = ProgressBarMode.Normal,
) {
    val colors = RolaboxMetal.colors
    val clamped = progress.coerceIn(0f, 1f)
    val fill = when (mode) {
        ProgressBarMode.Normal -> colors.displayFill
        ProgressBarMode.Scrub -> colors.accent
        ProgressBarMode.Volume -> colors.displayInk
    }
    val barHeight = if (mode == ProgressBarMode.Scrub) ScrubHeight else NormalHeight
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LabelsGap)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                }
                .drawBehind {
                    val corner = CornerRadius(BarCorner.toPx())
                    drawRoundRect(colors.displayRule, size = size, cornerRadius = corner)
                    drawRoundRect(fill, size = Size(size.width * clamped, size.height), cornerRadius = corner)
                    if (mode == ProgressBarMode.Scrub) {
                        // Past the bar's height on purpose: the knob is taller than the bar.
                        val knob = KnobSize.toPx()
                        val center = Offset(size.width * clamped, size.height / 2f)
                        rotate(KNOB_TURN_DEGREES, pivot = center) {
                            drawRoundRect(
                                colors.accent,
                                topLeft = center - Offset(knob / 2f, knob / 2f),
                                size = Size(knob, knob),
                                cornerRadius = CornerRadius(KnobCorner.toPx()),
                            )
                        }
                    }
                },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = RolaboxType.styles.displayMeta)
            Text(endLabel, style = RolaboxType.styles.displayMeta)
        }
    }
}

@Composable
private fun BarPreview(mode: ProgressBarMode, progress: Float, startLabel: String, endLabel: String) {
    RolaboxTheme {
        Box(Modifier.padding(PreviewPadding)) {
            // The display's white, since the bar is made for it and not for the metal.
            Box(Modifier.background(RolaboxMetal.colors.displayScreen).padding(PreviewPadding)) {
                DeviceProgressBar(progress, startLabel, endLabel, description = "Position", mode = mode)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceProgressBarPreview() = BarPreview(ProgressBarMode.Normal, PREVIEW_TIME, "1:12", "-2:29")

@PreviewLightDark
@Composable
private fun DeviceProgressBarScrubPreview() = BarPreview(ProgressBarMode.Scrub, PREVIEW_SCRUB, "1:48", "-1:53")

@PreviewLightDark
@Composable
private fun DeviceProgressBarVolumePreview() = BarPreview(ProgressBarMode.Volume, PREVIEW_VOLUME, "VOL", "62%")
