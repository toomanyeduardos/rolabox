package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
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
        SettingsViewModelImpl(sections.toSet(), userData).also { it.uiState.launchIn(it.viewModelScope) }

    @Test
    fun theValues_areLoaded_andWithNoSectionsThereAreOnlySettingsOwnRows() {
        val viewModel = viewModel()

        assertEquals(Clicker.ON, viewModel.uiState.value.clicker)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, viewModel.uiState.value.darkTheme)
        assertEquals(emptyList<SettingsRow>(), viewModel.uiState.value.sections)
        assertEquals(
            listOf(SettingsListRow.Clicker, SettingsListRow.Theme, SettingsListRow.MainMenu, SettingsListRow.About),
            settingsListRows(viewModel.uiState.value.sections),
        )
    }

    @Test
    fun aContributedSection_comesAfterSettingsOwnRows() {
        val account = FakeSection(SettingsSlot.Account)

        val viewModel = viewModel(account)

        assertEquals(listOf(account.row.value), viewModel.uiState.value.sections)
        assertEquals(
            SettingsListRow.Section(account.row.value),
            settingsListRows(viewModel.uiState.value.sections).last(),
        )
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
    fun clicker_changesInPlace_andIsStored() = runTest {
        val viewModel = viewModel()

        viewModel.onClickerChange()
        assertEquals(Clicker.OFF, viewModel.uiState.value.clicker)
        assertEquals(Clicker.OFF, userData.observeUserData().first().getOrNull()?.clicker)

        viewModel.onClickerChange()
        assertEquals(Clicker.ON, viewModel.uiState.value.clicker)
    }

    @Test
    fun theme_stepsThroughEveryValueAndAround_andIsStored() = runTest {
        val viewModel = viewModel()

        val seen = (1..DarkThemeConfig.entries.size).map {
            viewModel.onThemeChange()
            viewModel.uiState.value.darkTheme
        }

        assertEquals(listOf(DarkThemeConfig.LIGHT, DarkThemeConfig.DARK, DarkThemeConfig.FOLLOW_SYSTEM), seen)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, userData.observeUserData().first().getOrNull()?.darkThemeConfig)
    }

    @Test
    fun aChangeThatCannotBeSaved_isReported_untilShown_andTheValueStays() {
        val viewModel = viewModel()
        userData.writeError = StorageError.Unavailable

        viewModel.onClickerChange()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals(Clicker.ON, viewModel.uiState.value.clicker)

        viewModel.onSaveFailureShown()
        assertFalse(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun valuesThatCannotBeRead_showNothing_andCannotBeChanged() = runTest {
        userData.setReadError(StorageError.Corrupted)
        val viewModel = viewModel()

        viewModel.onClickerChange()
        viewModel.onThemeChange()

        assertNull(viewModel.uiState.value.clicker)
        assertNull(viewModel.uiState.value.darkTheme)
        assertFalse(viewModel.uiState.value.saveFailed)
    }
}
