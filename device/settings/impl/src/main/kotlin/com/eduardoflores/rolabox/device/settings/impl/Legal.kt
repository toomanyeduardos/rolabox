package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListAndText
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Legal, a device screen under About (card 96): Rolabox is drawn after a click-wheel player, so it says it isn't
 * affiliated with Apple and that iPod is Apple's trademark. The text is fixed, so there is no ViewModel. The wheel
 * scrolls it a line a step, as a licence's text. MENU, the host's, goes back to About.
 */
@Composable
internal fun LegalRoute() {
    var scrolledLines by rememberSaveable { mutableIntStateOf(0) }
    var scrollableLines by remember { mutableIntStateOf(0) }
    ScreenTitle(stringResource(R.string.settings_about_legal))
    HandleWheelEvents { event ->
        if (event is WheelEvent.Turn) scrolledLines = (scrolledLines + event.steps).coerceIn(0, scrollableLines)
    }
    LegalText(
        scrolledLines = scrolledLines,
        onScrollableLinesChange = { lines ->
            scrollableLines = lines
            // A larger font leaves fewer lines to scroll: the text stays at its end.
            scrolledLines = scrolledLines.coerceAtMost(lines)
        },
    )
}

/** The stateless text that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun LegalText(scrolledLines: Int, onScrollableLinesChange: (Int) -> Unit) {
    DeviceListAndText(
        rows = emptyList(),
        highlightedIndex = NO_HIGHLIGHT,
        meta = null,
        text = stringResource(R.string.settings_legal_text),
        scrolledLines = scrolledLines,
        onScrollableLinesChange = onScrollableLinesChange,
    )
}

private const val NO_HIGHLIGHT = -1

@PreviewLightDark
@Composable
private fun LegalPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Legal", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                LegalText(scrolledLines = 0, onScrollableLinesChange = {})
            }
        }
    }
}
