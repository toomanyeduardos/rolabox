package com.eduardoflores.rolabox.device.settings.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.settings.api.SettingsEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsKey
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import javax.inject.Inject

/** The settings list, and the screens of the sections that other parts contributed (ADR-020). */
internal class DefaultSettingsEntries @Inject constructor(sections: Set<@JvmSuppressWildcards SettingsSection>) :
    SettingsEntries {
    private val sections = arrangeSections(sections)

    override fun appStackEntries(
        scope: EntryProviderScope<NavKey>,
        onOpenFullScreen: (NavKey) -> Unit,
        onBack: () -> Unit,
    ) {
        scope.entry<SettingsKey> {
            SettingsRoute(sections = sections, onOpenFullScreen = onOpenFullScreen, onBack = onBack)
        }
        sections.forEach { it.appStackEntries(scope, onBack) }
    }
}
