package com.eduardoflores.rolabox.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.navigation3.runtime.entryProvider
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.core.designsystem.component.DeviceList
import com.eduardoflores.rolabox.core.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.device.DeviceHost
import com.eduardoflores.rolabox.core.device.DeviceScreen
import com.eduardoflores.rolabox.core.device.rememberScreenStack

/**
 * The signed-in app's destination (ADR-018): the device host with this app's screens (ADR-019).
 * `:app` gives the host its first screen and its entries, and pushes in response to their exits.
 */
@Composable
internal fun DeviceRoute(modifier: Modifier = Modifier) {
    val stack = rememberScreenStack(FirstPlaceholderKey)
    DeviceHost(
        stack = stack,
        title = stringResource(R.string.app_name),
        entryProvider = entryProvider { placeholderEntries(onDeeperClick = { stack.push(SecondPlaceholderKey) }) },
        // Received and ignored until playback exists.
        onPlaybackEvent = {},
        modifier = modifier,
    )
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
        DeviceScreen(title = stringResource(R.string.app_name), batteryLevel = 0.7f, onWheelEvent = {}) {
            DeviceList(rows = rows, highlightedIndex = 0)
        }
    }
}
