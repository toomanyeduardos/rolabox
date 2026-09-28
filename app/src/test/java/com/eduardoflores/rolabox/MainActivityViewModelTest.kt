package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.UserData
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
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
        val viewModel = MainActivityViewModel(userDataRepository)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }

        userDataRepository.setDarkThemeConfig(DarkThemeConfig.DARK)

        assertEquals(
            MainActivityUiState.Success(UserData(darkThemeConfig = DarkThemeConfig.DARK)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun readError_isPreferencesUnavailable() = runTest {
        val viewModel = MainActivityViewModel(userDataRepository)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }

        userDataRepository.setReadError(StorageError.Corrupted)

        assertEquals(MainActivityUiState.PreferencesUnavailable, viewModel.uiState.value)
    }
}
