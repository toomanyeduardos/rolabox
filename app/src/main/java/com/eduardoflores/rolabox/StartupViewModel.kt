package com.eduardoflores.rolabox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.core.domain.ResolveStartDestinationUseCase
import com.eduardoflores.rolabox.core.domain.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StartupViewModel @Inject constructor(resolveStartDestinationUseCase: ResolveStartDestinationUseCase) :
    ViewModel() {
    // Resolved once per ViewModel, with no delay of its own. It isn't stopped when the screen stops,
    // so coming back to the app never routes the user somewhere else.
    val uiState: StateFlow<StartupUiState> = flow<StartupUiState> {
        emit(StartupUiState.Ready(resolveStartDestinationUseCase()))
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = StartupUiState.Resolving)
}

sealed interface StartupUiState {
    /** The splash stays up while the auth state and the saved offline-mode choice are read. */
    data object Resolving : StartupUiState

    data class Ready(val destination: StartDestination) : StartupUiState
}
