package com.eduardoflores.rolabox.device

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.core.designsystem.component.DeviceBody
import com.eduardoflores.rolabox.core.designsystem.component.DeviceDisplay
import com.eduardoflores.rolabox.core.designsystem.component.DeviceList
import com.eduardoflores.rolabox.core.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.core.designsystem.component.Wheel
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent

/** The signed-in app's destination (ADR-018): the body, the display with its screen stack, and the wheel. */
@Composable
internal fun DeviceRoute(modifier: Modifier = Modifier) {
    val stack = rememberScreenStack()
    val inputs = remember { ScreenInputs() }
    val router = remember(stack, inputs) { WheelEventRouter(stack, inputs) }
    DeviceScreen(
        batteryLevel = rememberBatteryLevel(),
        onWheelEvent = router::route,
        modifier = modifier,
    ) {
        ScreenStackDisplay(stack, inputs, Modifier.fillMaxSize())
    }
}

/** Stateless, so it can be previewed and snapshotted: the display shows whatever [content] draws. */
@Composable
internal fun DeviceScreen(
    batteryLevel: Float,
    onWheelEvent: (WheelEvent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DeviceBody(wheel = { Wheel(onEvent = onWheelEvent) }, modifier = modifier) {
        DeviceDisplay(
            title = stringResource(R.string.app_name),
            batteryLevel = batteryLevel,
            modifier = Modifier.weight(1f),
            content = content,
        )
    }
}

@PreviewLightDark
@Composable
private fun DeviceScreenPreview() {
    val rows = listOf(
        DeviceListRow(stringResource(R.string.device_music), opensSubmenu = true),
        DeviceListRow(stringResource(R.string.device_podcasts), opensSubmenu = true),
        DeviceListRow(stringResource(R.string.device_audiobooks), opensSubmenu = true),
        DeviceListRow(stringResource(R.string.device_shuffle_songs)),
        DeviceListRow(stringResource(R.string.device_settings), opensSubmenu = true),
    )
    RolaboxTheme {
        DeviceScreen(batteryLevel = 0.7f, onWheelEvent = {}) {
            DeviceList(rows = rows, highlightedIndex = 0)
        }
    }
}
