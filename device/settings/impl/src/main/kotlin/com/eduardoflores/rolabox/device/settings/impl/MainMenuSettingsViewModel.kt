package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import kotlinx.coroutines.flow.StateFlow

internal data class MainMenuSettingsUiState(
    /** Whether each row is shown, or null until it has loaded, or if it can't be read: then no row shows a value. */
    val rows: Map<MainMenuRow, MainMenuRowVisibility>? = null,
    /** The last change couldn't be saved, until [MainMenuSettingsViewModel.onSaveFailureShown]. */
    val saveFailed: Boolean = false,
)

/** What Main Menu shows, and what the wheel does on it (ADR-021). [MainMenuSettingsViewModelImpl] implements it. */
internal abstract class MainMenuSettingsViewModel : ViewModel() {
    abstract val uiState: StateFlow<MainMenuSettingsUiState>

    /** Center on [row]: hides it when it is shown, and shows it when it is hidden. Nothing until it has loaded. */
    abstract fun onRowChange(row: MainMenuRow)

    /** The failure to save was shown, so it isn't shown again. */
    abstract fun onSaveFailureShown()
}
