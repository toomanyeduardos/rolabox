package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.api.Clicker
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

/** The settings are preferences, stored by `:common:userdata` (ADR-011): Settings stores nothing of its own. */
@HiltViewModel
internal class SettingsViewModelImpl @Inject constructor(
    sections: Set<@JvmSuppressWildcards SettingsSection>,
    private val userDataRepository: UserDataRepository,
) : SettingsViewModel() {
    private val saveFailed = MutableStateFlow(false)

    // A preference that can't be read looks the same as one that hasn't loaded: the row shows no value.
    private val userData = userDataRepository.observeUserData().map { it.getOrNull() }

    override val uiState: StateFlow<SettingsUiState> = combine(
        userData,
        sectionRows(arrangeSections(sections)),
        saveFailed,
    ) { userData, sections, saveFailed ->
        SettingsUiState(
            clicker = userData?.clicker,
            darkTheme = userData?.darkThemeConfig,
            sections = sections,
            saveFailed = saveFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    override fun onClickerChange() {
        val clicker = uiState.value.clicker ?: return
        save { userDataRepository.setClicker(clicker.next()) }
    }

    override fun onThemeChange() {
        val theme = uiState.value.darkTheme ?: return
        save { userDataRepository.setDarkThemeConfig(theme.next()) }
    }

    override fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private fun save(write: suspend () -> Either<StorageError, Unit>) {
        viewModelScope.launch { write().onLeft { saveFailed.value = true } }
    }

    // combine() of no flows never emits, and a list with no sections is what the device has without auth.
    private fun sectionRows(sections: List<SettingsSection>): Flow<List<SettingsRow>> =
        if (sections.isEmpty()) flowOf(emptyList()) else combine(sections.map { it.observeRow() }) { it.toList() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

// Center steps through the values in place, and around (the player the device is drawn after).
private fun Clicker.next(): Clicker = Clicker.entries.after(this)

private fun DarkThemeConfig.next(): DarkThemeConfig = DarkThemeConfig.entries.after(this)

private fun <T> List<T>.after(value: T): T = this[(indexOf(value) + 1) % size]
