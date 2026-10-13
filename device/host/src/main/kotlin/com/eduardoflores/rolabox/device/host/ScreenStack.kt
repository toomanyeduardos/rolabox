package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
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
 * one on top. A screen registers for MENU apart, and only while it is in a mode of its own, and for ⏯
 * apart, and only while its highlighted row can be played. A screen in a mode of its own also registers to
 * be told of the playback buttons, which it never takes.
 */
// A register, an unregister and a dispatch for each of the four ways a screen receives input.
@Suppress("TooManyFunctions")
internal class ScreenInputs {
    private val handlers = mutableMapOf<NavKey, (WheelEvent) -> Unit>()
    private val menuHandlers = mutableMapOf<NavKey, () -> Unit>()
    private val playPauseHandlers = mutableMapOf<NavKey, () -> Unit>()
    private val playbackButtonObservers = mutableMapOf<NavKey, () -> Unit>()

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

    fun registerPlayPause(key: NavKey, handler: () -> Unit) {
        playPauseHandlers[key] = handler
    }

    fun unregisterPlayPause(key: NavKey) {
        playPauseHandlers -= key
    }

    /** Gives ⏯ to the screen of [key] when it asked for it. False when it didn't, and ⏯ is a playback button. */
    fun dispatchPlayPause(key: NavKey): Boolean {
        val handler = playPauseHandlers[key] ?: return false
        handler()
        return true
    }

    fun registerPlaybackButtons(key: NavKey, observer: () -> Unit) {
        playbackButtonObservers[key] = observer
    }

    fun unregisterPlaybackButtons(key: NavKey) {
        playbackButtonObservers -= key
    }

    /** Tells the screen of [key] that a playback button was pressed, when it asked to be told. It doesn't take it. */
    fun notifyPlaybackButton(key: NavKey) {
        playbackButtonObservers[key]?.invoke()
    }
}

/**
 * The titles the screens gave for the display's header (ADR-019, rule 9). A screen gives its own by its
 * key, and the header shows the one of the screen on top of [ScreenStack]. A title is kept while its
 * screen is under the top one and no longer composed, so it is there when MENU returns to it.
 */
internal class ScreenTitles {
    private val titles = mutableStateMapOf<NavKey, String>()

    /** [title] is `null` while the screen doesn't know it yet. */
    fun set(key: NavKey, title: String?) {
        if (title == null) titles -= key else titles[key] = title
    }

    /** The title the screen of [key] gave, or `null` when it gave none. */
    fun titleOf(key: NavKey): String? = titles[key]

    /** Forgets the titles of the screens that left the stack, whose keys aren't in [keys]. */
    fun retainOnly(keys: List<NavKey>) {
        (titles.keys - keys.toSet()).forEach { titles -= it }
    }
}

/**
 * Decides where each wheel event goes (ADR-018, rule 5). The screens never see hold MENU, ⏮, ⏭ or
 * their holds, so none of them can change what those do. They see MENU only while they are in a mode of
 * their own, and ⏯ only while their highlighted row can be played (ADR-019, rule 6). The playback buttons
 * go to [onPlaybackEvent], so the host doesn't depend on the playback area (ADR-019). A top screen in a mode
 * of its own is told of them first, so the mode ends before the button does what it does (ADR-018, rule 16).
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

            WheelEvent.PlayPause -> if (!inputs.dispatchPlayPause(stack.top)) playbackButton(event)

            WheelEvent.Previous,
            WheelEvent.Next,
            is WheelEvent.HoldPrevious,
            is WheelEvent.HoldNext,
            -> playbackButton(event)
        }
    }

    private fun playbackButton(event: WheelEvent) {
        inputs.notifyPlaybackButton(stack.top)
        onPlaybackEvent(event)
    }
}
