package com.eduardoflores.rolabox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxAccent
import com.eduardoflores.rolabox.core.userdata.api.AccentColor
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.UserData
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainActivityViewModel @Inject constructor(userDataRepository: UserDataRepository) : ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = userDataRepository.observeUserData()
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

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Success(val userData: UserData) : MainActivityUiState

    /** The stored preferences couldn't be read. */
    data object PreferencesUnavailable : MainActivityUiState

    /** Falls back to the system setting until the stored preference has loaded, or if it can't be read. */
    fun shouldUseDarkTheme(isSystemDarkTheme: Boolean): Boolean = when (this) {
        Loading, PreferencesUnavailable -> isSystemDarkTheme

        is Success -> when (userData.darkThemeConfig) {
            DarkThemeConfig.FOLLOW_SYSTEM -> isSystemDarkTheme
            DarkThemeConfig.LIGHT -> false
            DarkThemeConfig.DARK -> true
        }
    }

    /** The default accent until the stored preference has loaded, or if it can't be read. */
    val accent: RolaboxAccent
        get() = when (this) {
            Loading, PreferencesUnavailable -> RolaboxAccent.Blue

            is Success -> when (userData.accentColor) {
                AccentColor.BLUE -> RolaboxAccent.Blue
                AccentColor.GREEN -> RolaboxAccent.Green
                AccentColor.PURPLE -> RolaboxAccent.Purple
                AccentColor.PINK -> RolaboxAccent.Pink
            }
        }
}
