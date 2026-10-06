package com.eduardoflores.rolabox

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.auth.data.api.StartDestination
import kotlinx.coroutines.flow.StateFlow

/** Whether the app knows yet where it opens (ADR-021). [DefaultStartupViewModel] implements it. */
abstract class StartupViewModel : ViewModel() {
    abstract val uiState: StateFlow<StartupUiState>
}

sealed interface StartupUiState {
    /** The splash stays up while the auth state and the saved offline-mode choice are read. */
    data object Resolving : StartupUiState

    data class Ready(val destination: StartDestination) : StartupUiState
}
