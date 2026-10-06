package com.eduardoflores.rolabox

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DefaultMainActivityViewModel @Inject constructor(userDataRepository: UserDataRepository) :
    MainActivityViewModel() {
    override val uiState: StateFlow<MainActivityUiState> = userDataRepository.observeUserData()
        .map { result ->
            // Every storage error looks the same here: the theme falls back to the system setting.
            result.fold(ifLeft = { MainActivityUiState.PreferencesUnavailable }, ifRight = MainActivityUiState::Success)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MainActivityUiState.Loading,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
