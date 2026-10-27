package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Each row is a preference of its own, stored by `:common:userdata` (ADR-011, rule 9). */
@HiltViewModel
internal class MainMenuSettingsViewModelImpl @Inject constructor(private val userDataRepository: UserDataRepository) :
    MainMenuSettingsViewModel() {
    private val saveFailed = MutableStateFlow(false)

    override val uiState: StateFlow<MainMenuSettingsUiState> = combine(
        userDataRepository.observeUserData(),
        saveFailed,
    ) { userData, saveFailed ->
        MainMenuSettingsUiState(rows = userData.getOrNull()?.mainMenuRows, saveFailed = saveFailed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MainMenuSettingsUiState(),
    )

    override fun onRowChange(row: MainMenuRow) {
        val visibility = uiState.value.rows?.get(row) ?: return
        val toggled = when (visibility) {
            MainMenuRowVisibility.SHOWN -> MainMenuRowVisibility.HIDDEN
            MainMenuRowVisibility.HIDDEN -> MainMenuRowVisibility.SHOWN
        }
        viewModelScope.launch {
            userDataRepository.setMainMenuRowVisibility(row, toggled).onLeft { saveFailed.value = true }
        }
    }

    override fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
