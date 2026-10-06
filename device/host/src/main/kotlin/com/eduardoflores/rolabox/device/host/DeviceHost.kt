package com.eduardoflores.rolabox.device.host

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceBody
import com.eduardoflores.rolabox.common.designsystem.component.DeviceDisplay
import com.eduardoflores.rolabox.common.designsystem.component.Wheel
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent

/**
 * The device (ADR-019): the body, the display with its screen stack, and the wheel. It runs the
 * stack and routes the wheel's events, and knows nothing about the screens it shows.
 *
 * The caller gives it the screens as [entryProvider], and pushes on [stack] in response to the exits
 * they report. The host pops. [onPlaybackEvent] receives ⏮, ⏭, ⏯ and the holds of ⏮ and ⏭, which are
 * the same on every screen (ADR-018, rule 5).
 */
@Composable
fun DeviceHost(
    stack: ScreenStack,
    title: String,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    onPlaybackEvent: (WheelEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPlaybackEvent by rememberUpdatedState(onPlaybackEvent)
    val inputs = remember { ScreenInputs() }
    val router = remember(stack, inputs) { WheelEventRouter(stack, inputs) { currentOnPlaybackEvent(it) } }
    DeviceScreen(
        title = title,
        batteryLevel = rememberBatteryLevel(),
        onWheelEvent = router::route,
        modifier = modifier,
    ) {
        ScreenStackDisplay(stack, inputs, entryProvider, Modifier.fillMaxSize())
    }
}

/** The assembled device. Stateless, so it can be previewed and snapshotted: the display shows what [content] draws. */
@Composable
fun DeviceScreen(
    title: String,
    batteryLevel: Float,
    onWheelEvent: (WheelEvent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DeviceBody(wheel = { Wheel(onEvent = onWheelEvent) }, modifier = modifier) {
        DeviceDisplay(
            title = title,
            batteryLevel = batteryLevel,
            modifier = Modifier.weight(1f),
            content = content,
        )
    }
}
