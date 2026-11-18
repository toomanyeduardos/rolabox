package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.api.EpisodesOnMobileData
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Use Mobile Data in Settings → Podcasts (ADR-031, frame 35). */
class PodcastSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userData = FakeUserDataRepository()

    private fun viewModel() = PodcastSettingsViewModelImpl(userData).also { it.uiState.launchIn(it.viewModelScope) }

    @Test
    fun useMobileData_isOnUntilTurnedOff() {
        assertEquals(EpisodesOnMobileData.ON, viewModel().uiState.value.mobileData)
    }

    @Test
    fun center_changesItInPlace_andStoresIt() = runTest {
        val viewModel = viewModel()

        viewModel.onMobileDataChange()

        assertEquals(EpisodesOnMobileData.OFF, viewModel.uiState.value.mobileData)
        assertEquals(EpisodesOnMobileData.OFF, userData.observeEpisodesOnMobileData().first().getOrNull())

        viewModel.onMobileDataChange()
        assertEquals(EpisodesOnMobileData.ON, viewModel.uiState.value.mobileData)
    }

    @Test
    fun aChangeThatCannotBeSaved_isReported() {
        val viewModel = viewModel()
        userData.writeError = StorageError.Unavailable

        viewModel.onMobileDataChange()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals(EpisodesOnMobileData.ON, viewModel.uiState.value.mobileData)
    }

    @Test
    fun aSettingThatCannotBeRead_showsNothing() {
        userData.setReadError(StorageError.Corrupted)

        assertNull(viewModel().uiState.value.mobileData)
    }
}
