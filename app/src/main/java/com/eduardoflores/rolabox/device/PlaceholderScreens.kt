package com.eduardoflores.rolabox.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.core.designsystem.component.DeviceList
import com.eduardoflores.rolabox.core.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.core.device.HandleWheelEvents
import com.eduardoflores.rolabox.core.device.rememberListHighlight
import kotlinx.serialization.Serializable

private const val PLACEHOLDER_ROWS = 8

/** The first placeholder screen, where the screen stack starts until the main menu exists (37.07). */
@Serializable
internal data object FirstPlaceholderKey : NavKey

/** The placeholder screen that the first one opens. */
@Serializable
internal data object SecondPlaceholderKey : NavKey

/** The device screens `:app` gives to the host until the main menu exists (37.07). */
internal fun EntryProviderScope<NavKey>.placeholderEntries(onDeeperClick: () -> Unit) {
    entry<FirstPlaceholderKey> {
        PlaceholderScreen(label = stringResource(R.string.device_placeholder_first), onCenter = onDeeperClick)
    }
    entry<SecondPlaceholderKey> {
        PlaceholderScreen(label = stringResource(R.string.device_placeholder_second), onCenter = {})
    }
}

/**
 * A stand-in for a real screen: a list whose highlight, saved with its entry, is moved by turning
 * the wheel, and whose center reports [onCenter]. Replaced by the main menu in 37.07.
 */
@Composable
private fun PlaceholderScreen(label: String, onCenter: () -> Unit) {
    val highlight = rememberListHighlight()
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, PLACEHOLDER_ROWS)
            WheelEvent.Center -> onCenter()
            else -> Unit
        }
    }
    DeviceList(rows = List(PLACEHOLDER_ROWS) { DeviceListRow("$label ${it + 1}") }, highlightedIndex = highlight.index)
}
