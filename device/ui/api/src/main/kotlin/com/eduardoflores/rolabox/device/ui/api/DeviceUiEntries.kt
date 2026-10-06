package com.eduardoflores.rolabox.device.ui.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of the device (ADR-020): it adds the device's entries, and takes the ways out
 * of them as lambdas. `:app` injects it, and never names what is under it.
 *
 * Only the entries of the app stack are added here. The entries of the screen stack inside the
 * display are given to the device host by the device itself (ADR-018, rule 2).
 */
interface DeviceUiEntries {
    /**
     * Adds [DeviceKey]'s entry, and the full screens of the device's parts, such as Settings.
     *
     * [onOpenFullScreen] is the generic exit: "open this key as a full screen". Only the main menu's
     * Settings row and the contributed settings sections use it (ADR-020, rule 17). [onBack] leaves
     * a full screen.
     */
    fun appStackEntries(scope: EntryProviderScope<NavKey>, onOpenFullScreen: (NavKey) -> Unit, onBack: () -> Unit)
}
