package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.UserData
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = FakeUserDataRepository()

    @Test
    fun storedPreferences_areSuccess() = runTest {
        val viewModel = MainActivityViewModelImpl(userDataRepository)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }

        userDataRepository.setDarkThemeConfig(DarkThemeConfig.DARK)
        userDataRepository.setAccentColor(AccentColor.PINK)

        assertEquals(
            MainActivityUiState.Success(
                UserData(darkThemeConfig = DarkThemeConfig.DARK, accentColor = AccentColor.PINK),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun readError_isPreferencesUnavailable() = runTest {
        val viewModel = MainActivityViewModelImpl(userDataRepository)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }

        userDataRepository.setReadError(StorageError.Corrupted)

        assertEquals(MainActivityUiState.PreferencesUnavailable, viewModel.uiState.value)
    }
}
