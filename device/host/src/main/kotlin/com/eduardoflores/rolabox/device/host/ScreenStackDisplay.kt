package com.eduardoflores.rolabox.device.host

import android.animation.ValueAnimator
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal

private const val SLIDE_MILLIS = 300
private const val BEHIND_PARALLAX = 3

/**
 * The display's content: a second `NavDisplay` for the screen stack (ADR-018). Going deeper slides
 * the new screen in from the right and going back slides it out, and only this content moves.
 *
 * The system's back is not handled here (ADR-018, rule 9): this `NavDisplay` is built from its
 * scene state, without the library's back handler, which would take back whenever there is a screen
 * under the top one. So from any depth back reaches the stack the device is on and leaves the app,
 * predictive back previews that and not the screen below, and only MENU is [ScreenStack.pop].
 */
@Composable
internal fun ScreenStackDisplay(
    stack: ScreenStack,
    inputs: ScreenInputs,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
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
    val entries = rememberDecoratedNavEntries(
        backStack = stack.keys,
        // A screen's highlight and ViewModels live as long as its entry is on the stack.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key -> deviceEntry(key, entryProvider(key), inputs) },
    )
    // One screen at a time, and no scene of the display asks to go back.
    val sceneState = rememberSceneState(entries, listOf(SinglePaneSceneStrategy()), onBack = {})
    NavDisplay(
        sceneState = sceneState,
        // Never given to a back handler, so no back gesture is ever in progress on the display.
        navigationEventState = rememberNavigationEventState(sceneState),
        modifier = modifier.clipToBounds(),
        transitionSpec = pushSpec,
        popTransitionSpec = popSpec,
    )
}

/** What the host adds around every screen: where it registers for its events, and an opaque background. */
private fun deviceEntry(key: NavKey, entry: NavEntry<NavKey>, inputs: ScreenInputs): NavEntry<NavKey> =
    NavEntry(navEntry = entry) {
        val input = remember(inputs, key) { ScreenInput(inputs, key) }
        CompositionLocalProvider(LocalScreenInput provides input) {
            // Opaque, so the screen that is leaving doesn't show through the one that is arriving.
            Box(Modifier.fillMaxSize().background(RolaboxMetal.colors.displayScreen)) {
                entry.Content()
            }
        }
    }

/** False when the user removed animations in the system settings (animator duration scale 0). */
@Composable
private fun rememberAnimationsEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }
