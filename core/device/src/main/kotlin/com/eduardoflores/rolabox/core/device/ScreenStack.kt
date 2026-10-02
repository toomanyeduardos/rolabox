package com.eduardoflores.rolabox.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent

/**
 * The stack of screens inside the device's display (ADR-018): linear, and starting at its first
 * screen. `:app` decides every push, and only the host pops, in response to the wheel and the
 * system's back (ADR-019).
 */
class ScreenStack internal constructor(private val backStack: NavBackStack<NavKey>) {
    val keys: List<NavKey> get() = backStack

    val top: NavKey get() = backStack.last()

    fun push(key: NavKey) {
        backStack.add(key)
    }

    /** Goes back one screen. The first screen stays: the system's back leaves the device from there. */
    internal fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Goes back to the first screen. */
    internal fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

/** The screen stack, starting at [startKey], saved across process death with the entry the device is in. */
@Composable
fun rememberScreenStack(startKey: NavKey): ScreenStack {
    val backStack = rememberNavBackStack(startKey)
    return remember(backStack) { ScreenStack(backStack) }
}

/**
 * Where the screens receive the wheel events that are theirs. A screen registers for its own key
 * while it is composed, so a screen that is still animating out never gets an event meant for the
 * one on top.
 */
internal class ScreenInputs {
    private val handlers = mutableMapOf<NavKey, (WheelEvent) -> Unit>()

    fun register(key: NavKey, handler: (WheelEvent) -> Unit) {
        handlers[key] = handler
    }

    fun unregister(key: NavKey) {
        handlers -= key
    }

    fun dispatch(key: NavKey, event: WheelEvent) {
        handlers[key]?.invoke(event)
    }
}

/**
 * Decides where each wheel event goes (ADR-018, rule 5). The screens never see MENU or the playback
 * buttons, so none of them can change what those do. The playback buttons go to [onPlaybackEvent],
 * so the host doesn't depend on the playback area (ADR-019).
 */
internal class WheelEventRouter(
    private val stack: ScreenStack,
    private val inputs: ScreenInputs,
    private val onPlaybackEvent: (WheelEvent) -> Unit,
) {
    fun route(event: WheelEvent) {
        when (event) {
            is WheelEvent.Turn, WheelEvent.Center -> inputs.dispatch(stack.top, event)

            WheelEvent.Menu -> stack.pop()

            WheelEvent.HoldMenu -> stack.popToRoot()

            WheelEvent.Previous,
            WheelEvent.Next,
            WheelEvent.PlayPause,
            is WheelEvent.HoldPrevious,
            is WheelEvent.HoldNext,
            -> onPlaybackEvent(event)
        }
    }
}
