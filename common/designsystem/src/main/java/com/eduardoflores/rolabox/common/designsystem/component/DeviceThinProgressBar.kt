package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

private val BarHeight = 3.dp
private val GapAbove = 6.dp
private val PreviewPadding = 16.dp
private const val PREVIEW_PROGRESS = 0.4f

/**
 * Work going on behind a list, along the bottom of the display: a line from edge to edge, filled up to
 * [progress] (0 to 1), with square ends and no labels or knob, so it doesn't read as [DeviceProgressBar].
 * Put it under the list, not over it, so that it never covers a row. [description] is what a screen reader
 * says for it, and its range is read as [progress].
 */
@Composable
fun DeviceThinProgressBar(progress: Float, description: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .padding(top = GapAbove)
            .fillMaxWidth()
            .height(BarHeight)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            }
            .drawBehind {
                drawRect(colors.displayRule)
                drawRect(colors.displayMuted, size = Size(size.width * clamped, size.height))
            },
    )
}

@PreviewLightDark
@Composable
private fun DeviceThinProgressBarPreview() {
    RolaboxTheme {
        Box(Modifier.background(RolaboxMetal.colors.displayScreen).padding(PreviewPadding)) {
            DeviceThinProgressBar(PREVIEW_PROGRESS, description = "Progress")
        }
    }
}
