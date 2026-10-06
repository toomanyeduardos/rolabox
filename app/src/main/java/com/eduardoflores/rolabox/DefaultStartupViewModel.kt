package com.eduardoflores.rolabox

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.auth.data.api.ResolveStartDestinationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DefaultStartupViewModel @Inject constructor(resolveStartDestinationUseCase: ResolveStartDestinationUseCase) :
    StartupViewModel() {
    // Resolved once per ViewModel, with no delay of its own. It isn't stopped when the screen stops,
    // so coming back to the app never routes the user somewhere else.
    override val uiState: StateFlow<StartupUiState> = flow<StartupUiState> {
        emit(StartupUiState.Ready(resolveStartDestinationUseCase()))
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = StartupUiState.Resolving)
}
