package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.core.domain.error.DatabaseError
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import com.eduardoflores.rolabox.core.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
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

        userDataRepository.setReadError(DatabaseError.Corrupted)

        assertEquals(MainActivityUiState.PreferencesUnavailable, viewModel.uiState.value)
    }
}
