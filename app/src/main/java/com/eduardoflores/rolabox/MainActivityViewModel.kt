package com.eduardoflores.rolabox

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxAccent
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.UserData
import kotlinx.coroutines.flow.StateFlow

/** What the activity's content needs to pick the theme (ADR-021). [MainActivityViewModelImpl] implements it. */
abstract class MainActivityViewModel : ViewModel() {
    abstract val uiState: StateFlow<MainActivityUiState>
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
