package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class SettingsViewModelImpl @Inject constructor(
    sections: Set<@JvmSuppressWildcards SettingsSection>,
    private val userDataRepository: UserDataRepository,
) : SettingsViewModel() {
    private val themeSaveFailed = MutableStateFlow(false)

    override val uiState: StateFlow<SettingsUiState> = combine(
        sectionRows(arrangeSections(sections)),
        // A theme that can't be read looks the same as one that hasn't loaded: nothing is selected.
        userDataRepository.observeUserData().map { result -> result.getOrNull()?.darkThemeConfig },
        themeSaveFailed,
        ::SettingsUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    override fun onDarkThemeSelect(config: DarkThemeConfig) {
        themeSaveFailed.value = false
        viewModelScope.launch {
            userDataRepository.setDarkThemeConfig(config).onLeft { themeSaveFailed.value = true }
        }
    }

    // combine() of no flows never emits, and a list with no sections is what the device has without auth.
    private fun sectionRows(sections: List<SettingsSection>): Flow<List<SettingsRow>> =
        if (sections.isEmpty()) flowOf(emptyList()) else combine(sections.map { it.observeRow() }) { it.toList() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
