package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent

/**
 * The stack of screens inside the device's display (ADR-018): linear, and starting at its first
 * screen. `:device:ui:impl` decides every push (ADR-020), and only the host pops, in response to the wheel
 * (ADR-019). The system's back never changes it: on every screen it leaves the app (ADR-018, rule 9). The one
 * pop the host is asked for is [popIfTop].
 */
class ScreenStack internal constructor(private val backStack: NavBackStack<NavKey>) {
    val keys: List<NavKey> get() = backStack

    val top: NavKey get() = backStack.last()

    fun push(key: NavKey) {
        backStack.add(key)
    }

    /**
     * Goes back one screen if [key] is the one on top, and does nothing otherwise. For a screen that can't stay
     * once what it shows is gone: Now Playing when the queue ends (ADR-018, rule 8). The first screen stays.
     */
    fun popIfTop(key: NavKey) {
        if (top == key) pop()
    }

    /** Goes back one screen, for MENU. The first screen stays, and MENU does nothing there. */
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
 * one on top. A screen registers for MENU apart, and only while it is in a mode of its own.
 */
internal class ScreenInputs {
    private val handlers = mutableMapOf<NavKey, (WheelEvent) -> Unit>()
    private val menuHandlers = mutableMapOf<NavKey, () -> Unit>()

    fun register(key: NavKey, handler: (WheelEvent) -> Unit) {
        handlers[key] = handler
    }

    fun unregister(key: NavKey) {
        handlers -= key
    }

    fun dispatch(key: NavKey, event: WheelEvent) {
        handlers[key]?.invoke(event)
    }

    fun registerMenu(key: NavKey, handler: () -> Unit) {
        menuHandlers[key] = handler
    }

    fun unregisterMenu(key: NavKey) {
        menuHandlers -= key
    }

    /** Gives MENU to the screen of [key] when it asked for it. False when it didn't, and MENU is the host's. */
    fun dispatchMenu(key: NavKey): Boolean {
        val handler = menuHandlers[key] ?: return false
        handler()
        return true
    }
}

/**
 * Decides where each wheel event goes (ADR-018, rule 5). The screens never see hold MENU or the
 * playback buttons, so none of them can change what those do, and they see MENU only while they are
 * in a mode of their own (ADR-019, rule 6). The playback buttons go to [onPlaybackEvent], so the
 * host doesn't depend on the playback area (ADR-019).
 */
internal class WheelEventRouter(
    private val stack: ScreenStack,
    private val inputs: ScreenInputs,
    private val onPlaybackEvent: (WheelEvent) -> Unit,
) {
    fun route(event: WheelEvent) {
        when (event) {
            is WheelEvent.Turn, WheelEvent.Center -> inputs.dispatch(stack.top, event)

            WheelEvent.Menu -> if (!inputs.dispatchMenu(stack.top)) stack.pop()

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
