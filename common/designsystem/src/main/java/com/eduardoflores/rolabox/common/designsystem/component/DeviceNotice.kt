package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val NoticeShape = RoundedCornerShape(4.dp)
private val NoticePadding = 8.dp
private val NoticeGap = 4.dp
private val PreviewSize = 200.dp
private val PreviewPadding = 16.dp
private const val TITLE_MAX_LINES = 2

/**
 * Something that just happened, said for a moment in the frame of a [DeviceCoverArtPlaceholder], whose place it takes.
 * The display can't scroll there, so a long [title] ends in an ellipsis, and [body] is a short line of its own. Its
 * size comes from [modifier], and it takes no input (ADR-018).
 */
@Composable
fun DeviceNotice(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Box(
        modifier
            .clip(NoticeShape)
            .background(Brush.linearGradient(listOf(colors.displayArt, colors.displayArtEnd)))
            .border(1.dp, colors.displayRule, NoticeShape)
            .padding(NoticePadding)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NoticeGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = RolaboxType.styles.displayBody,
                textAlign = TextAlign.Center,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                body,
                style = RolaboxType.styles.displayCaption.copy(color = colors.displayMuted),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceNoticePreview() {
    RolaboxTheme {
        Box(Modifier.padding(PreviewPadding)) {
            DeviceNotice(
                "Skipped Glasshouse and 2 more",
                "This file is damaged",
                Modifier.sizeIn(minWidth = PreviewSize, minHeight = PreviewSize),
            )
        }
    }
}
