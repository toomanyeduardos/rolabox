package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityUiStateTest {
    @Test
    fun loading_followsSystem() {
        assertEquals(true, MainActivityUiState.Loading.shouldUseDarkTheme(isSystemDarkTheme = true))
        assertEquals(false, MainActivityUiState.Loading.shouldUseDarkTheme(isSystemDarkTheme = false))
    }

    @Test
    fun preferencesUnavailable_followsSystem() {
        val state = MainActivityUiState.PreferencesUnavailable
        assertEquals(true, state.shouldUseDarkTheme(isSystemDarkTheme = true))
        assertEquals(false, state.shouldUseDarkTheme(isSystemDarkTheme = false))
    }

    @Test
    fun followSystem_followsSystem() {
        val state = success(DarkThemeConfig.FOLLOW_SYSTEM)
        assertEquals(true, state.shouldUseDarkTheme(isSystemDarkTheme = true))
        assertEquals(false, state.shouldUseDarkTheme(isSystemDarkTheme = false))
    }

    @Test
    fun light_ignoresSystem() {
        assertEquals(false, success(DarkThemeConfig.LIGHT).shouldUseDarkTheme(isSystemDarkTheme = true))
    }

    @Test
    fun dark_ignoresSystem() {
        assertEquals(true, success(DarkThemeConfig.DARK).shouldUseDarkTheme(isSystemDarkTheme = false))
    }

    private fun success(config: DarkThemeConfig) = MainActivityUiState.Success(UserData(darkThemeConfig = config))
}
