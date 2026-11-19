package com.eduardoflores.rolabox.device.host

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.eduardoflores.rolabox.common.designsystem.component.DeviceTextField

/**
 * Whether the device is typing into a screen's field, or is on the wheel (ADR-032). The host owns it: a screen
 * starts typing with [start], and typing ends when the keyboard goes, which only the host sees. A screen reads
 * [isTyping] to draw nothing highlighted while typing, and is told when typing ends by [TypingField].
 */
@Stable
class DeviceTyping internal constructor(typing: Boolean) {
    var isTyping by mutableStateOf(typing)
        private set

    /** Starts typing: the field takes focus and the keyboard comes up. For center on the highlighted field. */
    fun start() {
        isTyping = true
    }

    internal fun end() {
        isTyping = false
    }

    internal companion object {
        val Saver = Saver<DeviceTyping, Boolean>(save = { it.isTyping }, restore = { DeviceTyping(it) })
    }
}

/**
 * Whether the device is typing into the field of the screen that calls this, saved with its entry, so the
 * keyboard comes back after a configuration change or process death (ADR-032, rule 6). [typeWhenShown] starts
 * the screen typing, such as Search, which opens with the keyboard up.
 */
@Composable
fun rememberDeviceTyping(typeWhenShown: Boolean = false): DeviceTyping =
    rememberSaveable(saver = DeviceTyping.Saver) { DeviceTyping(typeWhenShown) }

/** The keyboard's action key, which a screen names. Either one ends typing. */
enum class TypingAction(internal val imeAction: ImeAction) {
    Search(ImeAction.Search),
    Done(ImeAction.Done),
}

/**
 * The field of a device screen (ADR-032): a screen places it as the `field` of its `DeviceList`, with its text
 * [state], which it owns and saves, its [hint] and the [shortHint] that replaces it when it doesn't fit. The
 * host does the rest: it gives the field focus while [typing], which shows the keyboard, stops the wheel, and
 * ends typing when the keyboard goes, whatever made it go. With a hardware keyboard, Enter and Esc end it.
 *
 * The keyboard's [action] key, and Enter, call [onAction] and then end typing. When typing ends,
 * [onTypingEnd] is called, and the screen highlights its first result, or the field when there is none
 * ([highlightAfterTyping]). The field is drawn [highlighted] only while the device isn't typing.
 */
@Composable
fun TypingField(
    typing: DeviceTyping,
    state: TextFieldState,
    hint: String,
    shortHint: String,
    highlighted: Boolean,
    onTypingEnd: () -> Unit,
    modifier: Modifier = Modifier,
    action: TypingAction = TypingAction.Search,
    onAction: () -> Unit = {},
) {
    val isTyping = typing.isTyping
    val currentOnTypingEnd by rememberUpdatedState(onTypingEnd)
    val currentOnAction by rememberUpdatedState(onAction)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val input = LocalScreenInput.current
    DisposableEffect(input, isTyping) {
        if (isTyping) input?.registerTyping { typing.end() }
        onDispose { input?.unregisterTyping() }
    }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    // Not saved: after a configuration change, typing that was on goes on, and ending it again isn't news.
    val wasTyping = remember { BooleanHolder(isTyping) }
    LaunchedEffect(isTyping) {
        if (isTyping) {
            wasTyping.value = true
            focusRequester.requestFocus()
            endWhenTheKeyboardGoes(typing) { ime.getBottom(density) }
        } else if (wasTyping.value) {
            // Losing focus hides the keyboard, when it isn't already gone.
            focusManager.clearFocus()
            wasTyping.value = false
            currentOnTypingEnd()
        }
    }
    DeviceTextField(
        state = state,
        hint = hint,
        shortHint = shortHint,
        typing = isTyping,
        highlighted = highlighted && !isTyping,
        modifier = modifier,
        textFieldModifier = Modifier
            .focusRequester(focusRequester)
            // Focus from anywhere else, such as TalkBack activating the field, is typing too.
            .onFocusChanged { if (it.isFocused && !typing.isTyping) typing.start() }
            .onPreviewKeyEvent { event -> typing.onHardwareKey(event) { currentOnAction() } },
        imeAction = action.imeAction,
        onKeyboardAction = {
            currentOnAction()
            typing.end()
        },
    )
}

/**
 * Ends [typing] when the keyboard goes, whatever made it go, from its height, [keyboardHeight]. A hardware
 * keyboard never shows one, so typing ends only after one has shown.
 */
private suspend fun endWhenTheKeyboardGoes(typing: DeviceTyping, keyboardHeight: () -> Int) {
    var shown = false
    snapshotFlow { keyboardHeight() > 0 }.collect { showing ->
        if (showing) {
            shown = true
        } else if (shown) {
            typing.end()
        }
    }
}

/** A hardware keyboard's Enter calls [onAction] and ends typing, and its Esc ends typing. True when it took [event]. */
private fun DeviceTyping.onHardwareKey(event: KeyEvent, onAction: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || !isTyping) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            onAction()
            end()
            true
        }

        Key.Escape -> {
            end()
            true
        }

        else -> false
    }
}

private class BooleanHolder(var value: Boolean)
