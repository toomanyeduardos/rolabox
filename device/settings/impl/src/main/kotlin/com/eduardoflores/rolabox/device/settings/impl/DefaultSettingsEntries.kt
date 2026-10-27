package com.eduardoflores.rolabox.device.settings.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.settings.api.AboutKey
import com.eduardoflores.rolabox.device.settings.api.MainMenuSettingsKey
import com.eduardoflores.rolabox.device.settings.api.SettingsEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsKey
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import javax.inject.Inject

/**
 * The settings screens in the display, and the full screens of the sections that other parts
 * contributed (ADR-020). This is the one place that names the screens' concrete ViewModels, since
 * Hilt creates a ViewModel by its class. The routes take the abstract ones (ADR-021).
 */
internal class DefaultSettingsEntries @Inject constructor(sections: Set<@JvmSuppressWildcards SettingsSection>) :
    SettingsEntries {
    private val sections = arrangeSections(sections)

    override fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onMainMenuClick: () -> Unit,
        onAboutClick: () -> Unit,
        onOpenFullScreen: (NavKey) -> Unit,
    ) = with(scope) {
        entry<SettingsKey> {
            SettingsRoute(
                viewModel = hiltViewModel<SettingsViewModelImpl>(),
                onMainMenuClick = onMainMenuClick,
                onAboutClick = onAboutClick,
                onOpenFullScreen = onOpenFullScreen,
            )
        }
        entry<MainMenuSettingsKey> { MainMenuSettingsRoute(viewModel = hiltViewModel<MainMenuSettingsViewModelImpl>()) }
        entry<AboutKey> { AboutRoute(viewModel = hiltViewModel<AboutViewModelImpl>()) }
    }

    override fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit) {
        sections.forEach { it.appStackEntries(scope, onBack) }
    }
}
