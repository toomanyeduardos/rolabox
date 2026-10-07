package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import kotlinx.coroutines.flow.StateFlow

internal data class SettingsUiState(
    /** The rows of the contributed sections, in the order of their slots. */
    val sections: List<SettingsRow> = emptyList(),
    /** The chosen theme, or null until it has loaded, or if it can't be read: then no choice is shown as selected. */
    val darkTheme: DarkThemeConfig? = null,
    /** The last attempt to save a theme failed. It is cleared by the next choice. */
    val themeSaveFailed: Boolean = false,
)

/** What Settings shows, and what the user does on it (ADR-021). [SettingsViewModelImpl] implements it. */
internal abstract class SettingsViewModel : ViewModel() {
    abstract val uiState: StateFlow<SettingsUiState>

    /** Saves the theme. The app applies it as soon as it is stored. */
    abstract fun onDarkThemeSelect(config: DarkThemeConfig)
}
