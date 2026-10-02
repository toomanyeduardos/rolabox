package com.eduardoflores.rolabox.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent

/** Where the screen of one entry registers for its events. The host provides one to each entry. */
internal class ScreenInput(private val inputs: ScreenInputs, private val key: NavKey) {
    fun register(handler: (WheelEvent) -> Unit) = inputs.register(key, handler)

    fun unregister() = inputs.unregister(key)
}

/** Null outside the device, such as in a preview, where a screen receives no events. */
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
