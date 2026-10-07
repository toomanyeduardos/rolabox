package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import kotlinx.coroutines.flow.launchIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userData = FakeUserDataRepository()

    // The state is only computed while someone observes it, as the screen does.
    private fun viewModel(vararg sections: FakeSection) =
        DefaultSettingsViewModel(sections.toSet(), userData).also { it.uiState.launchIn(it.viewModelScope) }

    @Test
    fun withNoSections_thereAreNoRows_andTheThemeIsLoaded() {
        val viewModel = viewModel()

        assertEquals(emptyList<SettingsRow>(), viewModel.uiState.value.sections)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, viewModel.uiState.value.darkTheme)
    }

    @Test
    fun theRows_areInTheOrderOfTheirSlots() {
        val account = FakeSection(SettingsSlot.Account)

        val viewModel = viewModel(account)

        assertEquals(listOf(account.row.value), viewModel.uiState.value.sections)
    }

    @Test
    fun aRowThatChanges_changesTheList() {
        val account = FakeSection(SettingsSlot.Account)
        val viewModel = viewModel(account)
        val changed = SettingsRow(title = { "Changed" })

        account.row.value = changed

        assertEquals(listOf(changed), viewModel.uiState.value.sections)
    }

    @Test
    fun selectingATheme_savesIt_andTheStateFollows() {
        val viewModel = viewModel()

        viewModel.onDarkThemeSelect(DarkThemeConfig.DARK)

        assertEquals(DarkThemeConfig.DARK, viewModel.uiState.value.darkTheme)
        assertFalse(viewModel.uiState.value.themeSaveFailed)
    }

    @Test
    fun aThemeThatCannotBeSaved_isReported_untilTheNextChoice() {
        val viewModel = viewModel()
        userData.writeError = StorageError.Unavailable

        viewModel.onDarkThemeSelect(DarkThemeConfig.DARK)

        assertTrue(viewModel.uiState.value.themeSaveFailed)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, viewModel.uiState.value.darkTheme)

        userData.writeError = null
        viewModel.onDarkThemeSelect(DarkThemeConfig.LIGHT)

        assertFalse(viewModel.uiState.value.themeSaveFailed)
        assertEquals(DarkThemeConfig.LIGHT, viewModel.uiState.value.darkTheme)
    }

    @Test
    fun aThemeThatCannotBeRead_hasNothingSelected() {
        userData.setReadError(StorageError.Corrupted)

        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.darkTheme)
    }
}
