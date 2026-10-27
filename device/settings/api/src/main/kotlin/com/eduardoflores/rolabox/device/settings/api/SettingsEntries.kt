package com.eduardoflores.rolabox.device.settings.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of Settings (ADR-020). The device adds it with its own entries, as the part's
 * parent, and maps its exits to keys. It keeps the two stacks apart (ADR-018, rule 2).
 */
interface SettingsEntries {
    /**
     * Adds the entries of [SettingsKey], [MainMenuSettingsKey] and [AboutKey], device screens for the
     * screen stack inside the display.
     *
     * The exits: center on Main Menu in the settings list is [onMainMenuClick], and on About
     * [onAboutClick]. Center on a contributed section's row is [onOpenFullScreen] with the key the row
     * opens: the generic exit, which leaves the display (ADR-020, rule 17).
     */
    fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onMainMenuClick: () -> Unit,
        onAboutClick: () -> Unit,
        onOpenFullScreen: (NavKey) -> Unit,
    )

    /**
     * Adds the full screens of the contributed sections that no other contract adds, on the app stack.
     * They are left through [onBack].
     */
    fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit)
}
