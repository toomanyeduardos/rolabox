package com.eduardoflores.rolabox

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries

/**
 * Adds the device and the full screens under it. `:app` implements the generic exit, "open this key
 * as a full screen", by pushing on the app stack (ADR-020), and never looks at the key.
 */
internal fun EntryProviderScope<NavKey>.deviceEntries(entries: DeviceUiEntries, navigator: AppNavigator) {
    entries.appStackEntries(scope = this, onOpenFullScreen = navigator::push, onBack = navigator::pop)
}
