package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import kotlinx.coroutines.flow.launchIn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LicencesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val activity = Licence("androidx.activity:activity", "Activity", "1.13.0", "Apache-2.0", "Apache text")
    private val re2j = Licence("com.google.re2j:re2j", "RE2/J", "1.8", "Go License", "Go text")
    private val loaded = Licences { listOf(activity, re2j).right() }
    private val unavailable = Licences { LicencesUnavailable.left() }

    private fun list(licences: Licences) =
        LicencesViewModelImpl(licences).also { it.uiState.launchIn(it.viewModelScope) }

    private fun licence(licences: Licences, libraryId: String) =
        LicenceViewModelImpl(libraryId, licences).also { it.uiState.launchIn(it.viewModelScope) }

    @Test
    fun theList_showsEveryLibrary() {
        assertEquals(LicencesUiState.Loaded(listOf(activity, re2j)), list(loaded).uiState.value)
    }

    @Test
    fun aListThatCannotBeRead_saysSo() {
        assertEquals(LicencesUiState.Unavailable, list(unavailable).uiState.value)
    }

    @Test
    fun aLicence_showsItsLibrary() {
        assertEquals(LicencesUiState.Loaded(re2j), licence(loaded, re2j.id).uiState.value)
    }

    @Test
    fun aLibraryNoLongerInTheList_saysSo() {
        assertEquals(LicencesUiState.Unavailable, licence(loaded, "gone:gone").uiState.value)
    }

    @Test
    fun aLicenceWhoseListCannotBeRead_saysSo() {
        assertEquals(LicencesUiState.Unavailable, licence(unavailable, activity.id).uiState.value)
    }
}
