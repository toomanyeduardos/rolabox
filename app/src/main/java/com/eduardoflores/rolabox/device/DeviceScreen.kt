package com.eduardoflores.rolabox.device

import android.util.Log
import androidx.compose.runtime.Composable
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

private const val TAG = "DeviceScreen"

/** The signed-in app's destination (ADR-018): the body, a static display and the wheel, with no Material chrome. */
@Composable
internal fun DeviceRoute(modifier: Modifier = Modifier) {
    DeviceScreen(
        batteryLevel = rememberBatteryLevel(),
        onWheelEvent = { Log.d(TAG, "Wheel event: $it") },
        modifier = modifier,
    )
}

/** Stateless, so it can be previewed and snapshotted. The screen stack and the event routing come in 37.06. */
@Composable
internal fun DeviceScreen(batteryLevel: Float, onWheelEvent: (WheelEvent) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        DeviceListRow(stringResource(R.string.device_music), opensSubmenu = true),
        DeviceListRow(stringResource(R.string.device_podcasts), opensSubmenu = true),
        DeviceListRow(stringResource(R.string.device_audiobooks), opensSubmenu = true),
        DeviceListRow(stringResource(R.string.device_shuffle_songs)),
        DeviceListRow(stringResource(R.string.device_settings), opensSubmenu = true),
    )
    DeviceBody(wheel = { Wheel(onEvent = onWheelEvent) }, modifier = modifier) {
        DeviceDisplay(
            title = stringResource(R.string.app_name),
            batteryLevel = batteryLevel,
            modifier = Modifier.weight(1f),
        ) {
            DeviceList(rows = rows, highlightedIndex = 0)
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceScreenPreview() {
    RolaboxTheme { DeviceScreen(batteryLevel = 0.7f, onWheelEvent = {}) }
}
