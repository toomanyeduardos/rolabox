package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OwnerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val podcasts = FakePodcastsRepository()

    private fun TestScope.observedViewModel(ownerId: OwnerId = PodcastSamples.nyTimes.id) =
        OwnerViewModelImpl(ownerId.value, podcasts).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.title.collect() }
        }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, OwnerViewModelImpl(PodcastSamples.nyTimes.id.value, podcasts).uiState.value)
    }

    @Test
    fun oneShow_isListed_asTheOwnerLevelIsAlwaysShown() = runTest {
        podcasts.owners = listOf(PodcastSamples.fieldNotesMedia)
        podcasts.shows = listOf(PodcastSamples.dailyDoseOfDesign)

        val viewModel = observedViewModel(PodcastSamples.fieldNotesMedia.id)

        assertEquals(ListUiState.Loaded(listOf(PodcastSamples.dailyDoseOfDesign)), viewModel.uiState.value)
        assertEquals(OwnerTitle("Field Notes Media"), viewModel.title.value)
    }

    @Test
    fun manyShows_areOnlyTheOwners() = runTest {
        podcasts.fillWithSamples()

        val shows = listOf(PodcastSamples.hardFork, PodcastSamples.theDaily, PodcastSamples.theEzraKleinShow)
        assertEquals(ListUiState.Loaded(shows), observedViewModel().uiState.value)
    }

    @Test
    fun ownerWithNoName_hasATitleWithNoName() = runTest {
        podcasts.fillWithSamples()

        assertEquals(OwnerTitle(null), observedViewModel(PodcastSamples.noOwner.id).title.value)
    }

    @Test
    fun ownerThatDisappears_isGone() = runTest {
        podcasts.fillWithSamples()
        val viewModel = observedViewModel()

        podcasts.shows = podcasts.shows.filter { it.ownerId != PodcastSamples.nyTimes.id }
        podcasts.owners = podcasts.owners - PodcastSamples.nyTimes

        assertEquals(ListUiState.Gone, viewModel.uiState.value)
        assertNull(viewModel.title.value)
    }

    @Test
    fun readError_isFailed_andLeavesNoTitle() = runTest {
        podcasts.fillWithSamples()
        podcasts.setReadError(StorageError.Corrupted)

        val viewModel = observedViewModel()

        assertEquals(ListUiState.Failed, viewModel.uiState.value)
        assertNull(viewModel.title.value)
    }
}
