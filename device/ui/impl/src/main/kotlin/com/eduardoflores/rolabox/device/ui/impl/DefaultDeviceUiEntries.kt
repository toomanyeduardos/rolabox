package com.eduardoflores.rolabox.device.ui.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.music.ui.api.MusicEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsEntries
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import javax.inject.Inject

/**
 * The device's entries on the app stack: the device itself, and the full screens of its parts, which
 * it gets by constructor injection and adds top down (ADR-020). The entries of the screen stack
 * inside the display are given to the host in [DeviceRoute].
 */
internal class DefaultDeviceUiEntries @Inject constructor(
    private val settingsEntries: SettingsEntries,
    private val musicEntries: MusicEntries,
) : DeviceUiEntries {
    override fun appStackEntries(
        scope: EntryProviderScope<NavKey>,
        onOpenFullScreen: (NavKey) -> Unit,
        onBack: () -> Unit,
    ) {
        scope.entry<DeviceKey> {
            DeviceRoute(
                musicEntries = musicEntries,
                onOpenFullScreen = onOpenFullScreen,
                modifier = Modifier.fillMaxSize(),
            )
        }
        settingsEntries.appStackEntries(scope, onOpenFullScreen, onBack)
    }
}
