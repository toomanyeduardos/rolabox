package com.eduardoflores.rolabox.device

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.core.designsystem.component.DeviceList
import com.eduardoflores.rolabox.core.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.splash.rememberAnimationsEnabled
import kotlinx.serialization.Serializable

private const val SLIDE_MILLIS = 300
private const val BEHIND_PARALLAX = 3
private const val PLACEHOLDER_ROWS = 8

/** The first placeholder screen, where the screen stack starts until the main menu exists (37.07). */
@Serializable
internal data object FirstPlaceholderKey : NavKey

/** The placeholder screen that the first one opens. */
@Serializable
internal data object SecondPlaceholderKey : NavKey

/** The screen stack, saved across process death with the device entry it is in. */
@Composable
internal fun rememberScreenStack(): ScreenStack {
    val backStack = rememberNavBackStack(FirstPlaceholderKey)
    return remember(backStack) { ScreenStack(backStack) }
}

/**
 * The display's content: a second `NavDisplay` for the screen stack (ADR-018). Going deeper slides
 * the new screen in from the right and going back slides it out, and only this content moves.
 *
 * The system's back is shared with the app's `NavDisplay` by the library: each one handles back
 * only while it has a screen to go back to, and the one composed deeper is asked first. So while the
 * display has screens above its first, back is [ScreenStack.pop], like MENU, and on the first screen
 * it falls through to the app stack, which leaves the app.
 */
@Composable
internal fun ScreenStackDisplay(stack: ScreenStack, inputs: ScreenInputs, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsEnabled()
    val pushSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (animate) {
            slideInHorizontally(tween(SLIDE_MILLIS)) { it } togetherWith
                slideOutHorizontally(tween(SLIDE_MILLIS)) { -it / BEHIND_PARALLAX }
        } else {
            EnterTransition.None togetherWith ExitTransition.None
        }
    }
    val popSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        val transform = if (animate) {
            slideInHorizontally(tween(SLIDE_MILLIS)) { -it / BEHIND_PARALLAX } togetherWith
                slideOutHorizontally(tween(SLIDE_MILLIS)) { it }
        } else {
            EnterTransition.None togetherWith ExitTransition.None
        }
        // The screen leaving is on top, so it slides out over the one coming back.
        transform.apply { targetContentZIndex = -1f }
    }
    NavDisplay(
        backStack = stack.keys,
        modifier = modifier.clipToBounds(),
        onBack = stack::pop,
        // A screen's highlight and ViewModels live as long as its entry is on the stack.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = pushSpec,
        popTransitionSpec = popSpec,
        predictivePopTransitionSpec = { popSpec() },
        entryProvider = entryProvider {
            entry<FirstPlaceholderKey> { key ->
                PlaceholderScreen(
                    key = key,
                    inputs = inputs,
                    label = stringResource(R.string.device_placeholder_first),
                    onCenter = { stack.push(SecondPlaceholderKey) },
                )
            }
            entry<SecondPlaceholderKey> { key ->
                PlaceholderScreen(
                    key = key,
                    inputs = inputs,
                    label = stringResource(R.string.device_placeholder_second),
                    onCenter = {},
                )
            }
        },
    )
}

/** Gives [handler] the events meant for the screen with this [key], for as long as it is composed. */
@Composable
internal fun HandleWheelEvents(key: NavKey, inputs: ScreenInputs, handler: (WheelEvent) -> Unit) {
    val currentHandler by rememberUpdatedState(handler)
    DisposableEffect(key, inputs) {
        inputs.register(key) { currentHandler(it) }
        onDispose { inputs.unregister(key) }
    }
}

/**
 * A stand-in for a real screen: a list whose highlight, saved with its entry, is moved by turning
 * the wheel, and whose center reports [onCenter]. Replaced by the main menu in 37.07.
 */
@Composable
private fun PlaceholderScreen(key: NavKey, inputs: ScreenInputs, label: String, onCenter: () -> Unit) {
    var highlighted by rememberSaveable { mutableIntStateOf(0) }
    HandleWheelEvents(key, inputs) { event ->
        when (event) {
            is WheelEvent.Turn -> highlighted = (highlighted + event.steps).coerceIn(0, PLACEHOLDER_ROWS - 1)
            WheelEvent.Center -> onCenter()
            else -> Unit
        }
    }
    val rows = List(PLACEHOLDER_ROWS) { DeviceListRow("$label ${it + 1}") }
    // Opaque, so the screen that is leaving doesn't show through the one that is arriving.
    Box(Modifier.fillMaxSize().background(RolaboxMetal.colors.displayScreen)) {
        DeviceList(rows = rows, highlightedIndex = highlighted)
    }
}
