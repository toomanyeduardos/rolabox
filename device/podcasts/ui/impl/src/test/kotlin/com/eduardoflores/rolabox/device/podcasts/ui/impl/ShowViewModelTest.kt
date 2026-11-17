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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val podcasts = FakePodcastsRepository()

    private fun TestScope.observedViewModel() = ShowViewModelImpl(PodcastSamples.theDaily.id.value, podcasts).also {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(ListUiState.Loading, ShowViewModelImpl(PodcastSamples.theDaily.id.value, podcasts).uiState.value)
    }

    @Test
    fun show_isLoadedWithItsEpisodes_inTheRepositorysOrder() = runTest {
        podcasts.fillWithSamples()

        val expected = ShowContent(PodcastSamples.theDaily, PodcastSamples.theDailyEpisodes)
        assertEquals(ListUiState.Loaded(expected), observedViewModel().uiState.value)
    }

    @Test
    fun showWithNoEpisodes_isLoadedWithFollowOnly() = runTest {
        podcasts.shows = listOf(PodcastSamples.hardFork.copy(id = PodcastSamples.theDaily.id))

        val state = observedViewModel().uiState.value as ListUiState.Loaded

        assertEquals(emptyList<Nothing>(), state.value.episodes)
    }

    @Test
    fun followOn_turnsOff() = runTest {
        podcasts.fillWithSamples()
        val viewModel = observedViewModel()

        viewModel.onFollowClick()

        assertFalse(podcasts.shows.first { it.id == PodcastSamples.theDaily.id }.isFollowed)
        assertFalse((viewModel.uiState.value as ListUiState.Loaded).value.show.isFollowed)
        assertFalse(viewModel.followFailed.value)
    }

    @Test
    fun followOff_turnsOn() = runTest {
        podcasts.fillWithSamples()
        podcasts.shows =
            podcasts.shows.map { if (it.id == PodcastSamples.theDaily.id) it.copy(isFollowed = false) else it }
        val viewModel = observedViewModel()

        viewModel.onFollowClick()

        assertEquals(true, (viewModel.uiState.value as ListUiState.Loaded).value.show.isFollowed)
    }

    @Test
    fun followThatCantBeSaved_keepsItsValue_andIsToldOnce() = runTest {
        podcasts.fillWithSamples()
        podcasts.setWriteError(StorageError.Unavailable)
        val viewModel = observedViewModel()

        viewModel.onFollowClick()

        assertTrue(viewModel.followFailed.value)
        assertTrue((viewModel.uiState.value as ListUiState.Loaded).value.show.isFollowed)

        viewModel.onFollowFailureShown()

        assertFalse(viewModel.followFailed.value)
    }

    @Test
    fun showThatDisappears_isGone() = runTest {
        podcasts.fillWithSamples()
        val viewModel = observedViewModel()

        podcasts.shows = podcasts.shows - PodcastSamples.theDaily

        assertEquals(ListUiState.Gone, viewModel.uiState.value)
    }

    @Test
    fun readError_isFailed() = runTest {
        podcasts.fillWithSamples()
        podcasts.setReadError(StorageError.Corrupted)

        assertEquals(ListUiState.Failed, observedViewModel().uiState.value)
    }
}
