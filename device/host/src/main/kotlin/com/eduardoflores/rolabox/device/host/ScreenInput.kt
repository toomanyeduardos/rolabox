package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent

/** Where the screen of one entry registers for its events and gives its title. The host provides one to each entry. */
internal class ScreenInput(
    private val inputs: ScreenInputs,
    private val titles: ScreenTitles,
    private val key: NavKey,
) {
    fun register(handler: (WheelEvent) -> Unit) = inputs.register(key, handler)

    fun unregister() = inputs.unregister(key)

    fun registerMenu(handler: () -> Unit) = inputs.registerMenu(key, handler)

    fun unregisterMenu() = inputs.unregisterMenu(key)

    fun registerPlayPause(handler: () -> Unit) = inputs.registerPlayPause(key, handler)

    fun unregisterPlayPause() = inputs.unregisterPlayPause(key)

    fun registerPlaybackButtons(observer: () -> Unit) = inputs.registerPlaybackButtons(key, observer)

    fun unregisterPlaybackButtons() = inputs.unregisterPlaybackButtons(key)

    fun setTitle(title: String?) = titles.set(key, title)
}

/** Null outside the device, such as in a preview, where a screen receives no events and its title goes nowhere. */
internal val LocalScreenInput = staticCompositionLocalOf<ScreenInput?> { null }

/**
 * Gives [handler] the wheel events meant for the device screen that calls this, for as long as it
 * is composed: turn and center, and only while the screen is on top of the stack (ADR-018, rule 5).
 * The screen doesn't name its key, and never sees the stack or the router (ADR-019, rule 6).
 */
@Composable
fun HandleWheelEvents(handler: (WheelEvent) -> Unit) {
    val input = LocalScreenInput.current ?: return
    val currentHandler by rememberUpdatedState(handler)
    DisposableEffect(input) {
        input.register { currentHandler(it) }
        onDispose { input.unregister() }
    }
}

/**
 * While [enabled], MENU goes to [onMenu] and the host doesn't go back a screen. It is for a device
 * screen that is in a mode of its own, such as scrub on Now Playing, to leave that mode first
 * (ADR-018, rule 16, and ADR-019, rule 6). Hold MENU is always the host's.
 */
@Composable
fun HandleMenu(enabled: Boolean, onMenu: () -> Unit) {
    val input = LocalScreenInput.current ?: return
    val currentOnMenu by rememberUpdatedState(onMenu)
    DisposableEffect(input, enabled) {
        if (enabled) input.registerMenu { currentOnMenu() }
        onDispose { input.unregisterMenu() }
    }
}

/**
 * While [enabled], ⏯ goes to [onPlayPause] and not to the device's handler of the playback buttons. It is for
 * a device screen whose highlighted row can be played, such as a list of songs, to play that row (ADR-018,
 * rule 5, and ADR-019, rule 6). On every other screen ⏯ plays or pauses what is loaded, and no screen sees it.
 */
@Composable
fun HandlePlayPause(enabled: Boolean, onPlayPause: () -> Unit) {
    val input = LocalScreenInput.current ?: return
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    DisposableEffect(input, enabled) {
        if (enabled) input.registerPlayPause { currentOnPlayPause() }
        onDispose { input.unregisterPlayPause() }
    }
}

/**
 * While [enabled], [onPlaybackButton] is called when ⏮, ⏭ or ⏯ is pressed, or ⏮ or ⏭ is held or released,
 * before the device's handler of the playback buttons receives it. It is for a device screen that is in a
 * mode of its own, such as scrub on Now Playing, to leave that mode: the button wins over the mode (ADR-018,
 * rule 16, and ADR-019, rule 6). The screen doesn't take the button and isn't told which one it was, so it
 * can't change what the button does.
 */
@Composable
fun ObservePlaybackButtons(enabled: Boolean, onPlaybackButton: () -> Unit) {
    val input = LocalScreenInput.current ?: return
    val currentOnPlaybackButton by rememberUpdatedState(onPlaybackButton)
    DisposableEffect(input, enabled) {
        if (enabled) input.registerPlaybackButtons { currentOnPlaybackButton() }
        onDispose { input.unregisterPlaybackButtons() }
    }
}

/**
 * Gives the display's header the [title] of the device screen that calls this: the header shows the title
 * of the screen on top of the stack (ADR-018, rule 19, and ADR-019, rule 9). Every device screen calls it.
 * A title that is loaded, such as an artist's name, is `null` until it is known, and the header shows none.
 *
 * The host keeps the title while the screen is on the stack, so it is there when MENU returns to it.
 */
@Composable
fun ScreenTitle(title: String?) {
    val input = LocalScreenInput.current ?: return
    DisposableEffect(input, title) {
        input.setTitle(title)
        onDispose {}
    }
}
