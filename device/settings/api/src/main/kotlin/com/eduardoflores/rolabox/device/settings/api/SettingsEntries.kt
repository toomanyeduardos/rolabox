package com.eduardoflores.rolabox.device.settings.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/** The entry contract of Settings (ADR-020). The device adds it with its own entries, as the part's parent. */
interface SettingsEntries {
    /**
     * Adds [SettingsKey]'s entry and the entries of the contributed sections, all full screens on
     * the app stack. A row is opened through [onOpenFullScreen], the generic exit, and [onBack]
     * leaves a screen.
     */
    fun appStackEntries(scope: EntryProviderScope<NavKey>, onOpenFullScreen: (NavKey) -> Unit, onBack: () -> Unit)
}
