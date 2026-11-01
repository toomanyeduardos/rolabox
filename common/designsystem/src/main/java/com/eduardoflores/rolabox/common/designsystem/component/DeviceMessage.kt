package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val MessagePadding = 16.dp
private val TitleGap = 8.dp
private val DisplayPreviewHeight = 318.dp

/**
 * What a device screen says in place of a list: that it is empty, or that it couldn't be read. It is
 * centered in the display, wraps at any font scale, and takes no input (ADR-018). Put it in the
 * content of a [DeviceDisplay].
 */
@Composable
fun DeviceMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(MessagePadding), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = RolaboxType.styles.displayRow.copy(color = RolaboxMetal.colors.displayMuted),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A [DeviceMessage] with something more to say: a [title], and under it a [body] in a quieter style, such
 * as what to do about it.
 */
@Composable
fun DeviceMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(MessagePadding),
        verticalArrangement = Arrangement.spacedBy(TitleGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = RolaboxType.styles.displayHeadline, textAlign = TextAlign.Center)
        Text(
            body,
            style = RolaboxType.styles.displayBody.copy(color = RolaboxMetal.colors.displayMuted),
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun DeviceMessageWithBodyPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Music", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceMessage("Nothing here", "Something to do about it, on a line or two of its own.")
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceMessagePreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Artists", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceMessage("No artists yet")
            }
        }
    }
}
