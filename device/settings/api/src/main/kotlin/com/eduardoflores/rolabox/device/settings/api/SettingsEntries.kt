package com.eduardoflores.rolabox.device.settings.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of Settings (ADR-020). The device adds it with its own entries, as the part's
 * parent, and maps its exits to keys. It keeps the two stacks apart (ADR-018, rule 2).
 */
interface SettingsEntries {
    /**
     * Adds the entries of [SettingsKey], [MainMenuSettingsKey], [PodcastSettingsKey], [AboutKey], [LicencesKey],
     * [LicenceKey] and [LegalKey], device screens for the screen stack inside the display.
     *
     * The exits: center on Main Menu in the settings list is [onMainMenuClick], on Podcasts [onPodcastsClick], and on
     * About [onAboutClick]. Center on Open-source Licences in About is [onLicencesClick], and on a library in that list
     * [onLicenceClick] with its key, and on Legal in About [onLegalClick]. Center on a contributed section's row is
     * [onOpenFullScreen] with the key the row opens: the generic exit, which leaves the display (ADR-020, rule 17).
     */
    @Suppress("LongParameterList") // One lambda per exit (ADR-020, rule 11).
    fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onMainMenuClick: () -> Unit,
        onPodcastsClick: () -> Unit,
        onAboutClick: () -> Unit,
        onLicencesClick: () -> Unit,
        onLicenceClick: (LicenceKey) -> Unit,
        onLegalClick: () -> Unit,
        onOpenFullScreen: (NavKey) -> Unit,
    )

    /**
     * Adds the full screens of the contributed sections that no other contract adds, on the app stack.
     * They are left through [onBack].
     */
    fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit)
}
