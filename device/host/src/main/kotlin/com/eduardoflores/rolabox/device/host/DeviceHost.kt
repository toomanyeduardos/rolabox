package com.eduardoflores.rolabox.device.host

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceBody
import com.eduardoflores.rolabox.common.designsystem.component.DeviceDisplay
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.component.Wheel
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent

/**
 * The device (ADR-019): the body, the display with its screen stack, and the wheel. It runs the
 * stack and routes the wheel's events, and knows nothing about the screens it shows.
 *
 * The caller gives it the screens as [entryProvider], and pushes on [stack] in response to the exits
 * they report. The host pops. [onPlaybackEvent] receives ⏮, ⏭ and their holds, which are the same on
 * every screen, and ⏯ unless the top screen plays its highlighted row with it (ADR-018, rule 5).
 *
 * The header shows the title that the top screen gives with [ScreenTitle], and none until it has. Its
 * record is playback state, which the host can't see, so the caller says whether an item is loaded,
 * [isItemLoaded], and whether it is playing, [isItemPlaying] (ADR-019, rule 9).
 */
@Composable
fun DeviceHost(
    stack: ScreenStack,
    isItemLoaded: Boolean,
    isItemPlaying: Boolean,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    onPlaybackEvent: (WheelEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPlaybackEvent by rememberUpdatedState(onPlaybackEvent)
    val inputs = remember { ScreenInputs() }
    val router = remember(stack, inputs) { WheelEventRouter(stack, inputs) { currentOnPlaybackEvent(it) } }
    val titles = remember { ScreenTitles() }
    val keys = stack.keys.toList()
    LaunchedEffect(keys) { titles.retainOnly(keys) }
    DeviceScreen(
        title = titles.titleOf(stack.top).orEmpty(),
        batteryLevel = rememberBatteryLevel(),
        onWheelEvent = router::route,
        modifier = modifier,
        record = recordOf(isItemLoaded, isItemPlaying),
    ) {
        ScreenStackDisplay(stack, inputs, titles, entryProvider, Modifier.fillMaxSize())
    }
}

/** The header's record: shown while an item is loaded, spinning while it plays and still while it is paused. */
internal fun recordOf(isItemLoaded: Boolean, isItemPlaying: Boolean): DeviceRecord = when {
    !isItemLoaded -> DeviceRecord.Hidden
    isItemPlaying -> DeviceRecord.Spinning
    else -> DeviceRecord.Still
}

/**
 * The assembled device. Stateless, so it can be previewed and snapshotted: the display shows what [content]
 * draws, and the header the [title] and the [record] it is given.
 */
@Composable
fun DeviceScreen(
    title: String,
    batteryLevel: Float,
    onWheelEvent: (WheelEvent) -> Unit,
    modifier: Modifier = Modifier,
    record: DeviceRecord = DeviceRecord.Hidden,
    content: @Composable () -> Unit,
) {
    DeviceBody(wheel = { Wheel(onEvent = onWheelEvent) }, modifier = modifier) {
        DeviceDisplay(
            title = title,
            batteryLevel = batteryLevel,
            modifier = Modifier.weight(1f),
            record = record,
            content = content,
        )
    }
}
