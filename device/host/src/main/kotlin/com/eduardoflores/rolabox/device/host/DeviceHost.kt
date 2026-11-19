package com.eduardoflores.rolabox.device.host

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * every screen, and ⏯ unless the top screen plays its highlighted row with it (ADR-018, rule 5). A top
 * screen in a mode of its own is told of them first, with [ObservePlaybackButtons].
 *
 * The header shows the title that the top screen gives with [ScreenTitle], and none until it has. Its
 * record is playback state, which the host can't see, so the caller says whether an item is loaded,
 * [isItemLoaded], and whether it is playing, [isItemPlaying] (ADR-019, rule 9). Whether the wheel clicks,
 * [clicks], is a setting, which the caller reads for it too.
 *
 * While the top screen's [TypingField] is typing (ADR-032), the wheel sends nothing, and a touch anywhere in the
 * device only ends typing. The phone's keyboard is drawn over the wheel, and when it would cover the display,
 * the device moves up just enough to keep the display above it.
 */
@Composable
fun DeviceHost(
    stack: ScreenStack,
    isItemLoaded: Boolean,
    isItemPlaying: Boolean,
    clicks: Boolean,
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
    val display = remember { DisplayPlacement() }
    val keyboardTop = keyboardTopInWindow(display.windowHeight)
    val liftPx = keyboardLift(display.bottomUnlifted, keyboardTop)
    val currentLiftPx by rememberUpdatedState(liftPx)
    Box(modifier) {
        DeviceScreen(
            title = titles.titleOf(stack.top).orEmpty(),
            batteryLevel = rememberBatteryLevel(),
            onWheelEvent = router::route,
            modifier = Modifier.fillMaxSize(),
            record = recordOf(isItemLoaded, isItemPlaying),
            clicks = clicks,
            lift = with(LocalDensity.current) { liftPx.toDp() },
            displayModifier = Modifier.onGloballyPositioned { display.update(it, currentLiftPx) },
        ) {
            ScreenStackDisplay(stack, inputs, titles, entryProvider, Modifier.fillMaxSize())
        }
        if (inputs.isTyping(stack.top)) {
            val top = stack.top
            // Over the whole device, so a touch outside the keyboard only ends typing (ADR-032, rule 3).
            Box(Modifier.matchParentSize().pointerInput(top) { endTypingOnTouch { inputs.endTyping(top) } })
        }
    }
}

/** Where the display is in the window, before the device moves up, and how tall the window is. */
private class DisplayPlacement {
    var bottomUnlifted by mutableFloatStateOf(0f)
        private set
    var windowHeight by mutableIntStateOf(0)
        private set

    fun update(coordinates: LayoutCoordinates, liftPx: Float) {
        bottomUnlifted = coordinates.positionInRoot().y + coordinates.size.height + liftPx
        windowHeight = coordinates.findRootCoordinates().size.height
    }
}

/**
 * The top of the keyboard in the window, from its inset, which only the host reads (ADR-032, rule 2). It is the
 * window's bottom while there is no keyboard, and follows the keyboard as it opens and closes.
 */
@Composable
private fun keyboardTopInWindow(windowHeight: Int): Int =
    windowHeight - WindowInsets.ime.getBottom(LocalDensity.current)

/**
 * How far the device moves up so the display's bottom edge, at [displayBottom] before it moves, is above the
 * keyboard's top, at [keyboardTop]: only as far as that takes, and not at all while the keyboard is under it.
 */
internal fun keyboardLift(displayBottom: Float, keyboardTop: Int): Float =
    (displayBottom - keyboardTop).coerceAtLeast(0f)

/** Takes every touch: the first one's down calls [onTouch], and the rest of that gesture goes nowhere. */
private suspend fun PointerInputScope.endTypingOnTouch(onTouch: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        onTouch()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
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
 * draws, and the header the [title] and the [record] it is given. The wheel clicks when [clicks], and is silent
 * in previews. [lift] moves the device up over the keyboard, and [displayModifier] is the display's.
 */
@Composable
fun DeviceScreen(
    title: String,
    batteryLevel: Float,
    onWheelEvent: (WheelEvent) -> Unit,
    modifier: Modifier = Modifier,
    displayModifier: Modifier = Modifier,
    record: DeviceRecord = DeviceRecord.Hidden,
    clicks: Boolean = false,
    lift: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    DeviceBody(wheel = { Wheel(onEvent = onWheelEvent, clicks = clicks) }, modifier = modifier, lift = lift) {
        DeviceDisplay(
            title = title,
            batteryLevel = batteryLevel,
            modifier = Modifier.weight(1f).then(displayModifier),
            record = record,
            content = content,
        )
    }
}
