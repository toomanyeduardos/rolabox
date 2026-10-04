package com.eduardoflores.rolabox.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.core.device.DeviceHost
import com.eduardoflores.rolabox.core.device.rememberScreenStack

/**
 * The signed-in app's destination (ADR-018): the device host with this app's screens (ADR-019).
 * `:app` gives the host its first screen and its entries, and pushes in response to their exits.
 */
@Composable
internal fun DeviceRoute(modifier: Modifier = Modifier) {
    val stack = rememberScreenStack(MainMenuKey)
    DeviceHost(
        stack = stack,
        title = stringResource(R.string.app_name),
        // Opens the Music menu once it exists (37.09).
        entryProvider = entryProvider { mainMenuEntry(onMusicClick = {}) },
        // Received and ignored until playback exists.
        onPlaybackEvent = {},
        modifier = modifier,
    )
}
