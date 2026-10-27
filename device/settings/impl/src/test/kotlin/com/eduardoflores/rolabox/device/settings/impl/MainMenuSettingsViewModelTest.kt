package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainMenuSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userData = FakeUserDataRepository()

    private fun viewModel() = MainMenuSettingsViewModelImpl(userData).also { it.uiState.launchIn(it.viewModelScope) }

    @Test
    fun everyRow_isShownUntilHidden() {
        assertEquals(AllShown, viewModel().uiState.value.rows)
    }

    @Test
    fun aRow_changesInPlace_andIsStored_andTheOthersStay() = runTest {
        val viewModel = viewModel()

        viewModel.onRowChange(MainMenuRow.PODCASTS)

        val expected = AllShown + (MainMenuRow.PODCASTS to MainMenuRowVisibility.HIDDEN)
        assertEquals(expected, viewModel.uiState.value.rows)
        assertEquals(expected, userData.observeUserData().first().getOrNull()?.mainMenuRows)

        viewModel.onRowChange(MainMenuRow.PODCASTS)
        assertEquals(AllShown, viewModel.uiState.value.rows)
    }

    @Test
    fun aChangeThatCannotBeSaved_isReported() {
        val viewModel = viewModel()
        userData.writeError = StorageError.Unavailable

        viewModel.onRowChange(MainMenuRow.MUSIC)

        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals(AllShown, viewModel.uiState.value.rows)
    }

    @Test
    fun rowsThatCannotBeRead_showNothing() {
        userData.setReadError(StorageError.Corrupted)

        assertNull(viewModel().uiState.value.rows)
    }
}

private val AllShown = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN }
