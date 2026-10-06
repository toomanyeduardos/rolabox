package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.eduardoflores.rolabox.device.host.DeviceHost
import com.eduardoflores.rolabox.device.host.rememberScreenStack
import com.eduardoflores.rolabox.device.settings.api.SettingsKey
import com.eduardoflores.rolabox.device.ui.api.MainMenuKey

/**
 * The device's destination (ADR-018): the device host with this product's screens. The device
 * assembles itself here (ADR-020): it gives the host its first screen and its entries, maps their
 * exits to keys, pushes on the screen stack, and handles the playback buttons.
 *
 * [onOpenFullScreen] is the generic exit, which leaves the display for a full screen on the app stack.
 */
@Composable
internal fun DeviceRoute(onOpenFullScreen: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    val stack = rememberScreenStack(MainMenuKey)
    DeviceHost(
        stack = stack,
        title = stringResource(R.string.device_title),
        entryProvider = entryProvider {
            mainMenuEntry(
                // Opens the Music menu once it exists (37.09).
                onMusicClick = {},
                onSettingsClick = { onOpenFullScreen(SettingsKey) },
            )
        },
        // Received and ignored until playback exists.
        onPlaybackEvent = {},
        modifier = modifier,
    )
}
