package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PodcastsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val podcasts = FakePodcastsRepository()

    private fun TestScope.observedViewModel() = PodcastsViewModelImpl(podcasts).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, PodcastsViewModelImpl(podcasts).uiState.value)
    }

    @Test
    fun emptyLibrary_isLoadedWithNoOwners() = runTest {
        assertEquals(ListUiState.Loaded(emptyList<Nothing>()), observedViewModel().uiState.value)
    }

    @Test
    fun oneOwner_isLoaded() = runTest {
        podcasts.owners = listOf(PodcastSamples.nyTimes)

        assertEquals(ListUiState.Loaded(listOf(PodcastSamples.nyTimes)), observedViewModel().uiState.value)
    }

    @Test
    fun manyOwners_keepTheRepositorysOrder() = runTest {
        podcasts.fillWithSamples()

        assertEquals(ListUiState.Loaded(PodcastSamples.owners), observedViewModel().uiState.value)
    }

    @Test
    fun ownersAdded_areFollowed() = runTest {
        val viewModel = observedViewModel()

        podcasts.owners = listOf(PodcastSamples.nyTimes)

        assertEquals(ListUiState.Loaded(listOf(PodcastSamples.nyTimes)), viewModel.uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        podcasts.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, observedViewModel().uiState.value)
    }

    @Test
    fun rows_startWithSearch_andAnOwnerWithNoName_isUnknown() {
        val rows = podcastsRows(listOf(PodcastSamples.nyTimes, PodcastSamples.noOwner), "Search", "Unknown owner")

        assertEquals(listOf("Search", "NY Times", "Unknown owner"), rows.map { it.label })
        assertEquals(listOf(true, true, true), rows.map { it.opensSubmenu })
    }
}
