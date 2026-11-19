package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.SavedStateHandle
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.testing.FakeEpisodeNetwork
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchEpisodeKey
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FoundEpisodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val directory = FakePodcastDirectory()
    private val podcasts = FakePodcastsRepository()
    private val playback = FakePlayback()
    private val network = FakeEpisodeNetwork()

    private val show = DirectoryShow(DirectoryId.itunes(99), "The Daily Stranger", "Someone", "https://e.com/s")
    private val battery = DirectoryEpisode(
        guid = "g2",
        title = "Inside the Race for a Cheaper Battery",
        publishedAt = Instant.parse("2026-11-10T12:00:00Z"),
        length = 28.minutes,
        description = "Batteries.",
        audioUrl = "https://e.com/2.mp3",
        audioSizeBytes = 27_000_000,
        audioType = "audio/mpeg",
    )
    private val noGuid = battery.copy(guid = null, title = "No GUID", audioUrl = "https://e.com/3.mp3")

    init {
        directory.feeds = mapOf(show to ShowFeed(show, "Someone", listOf(battery, noGuid)))
    }

    private fun TestScope.observedViewModel(key: PodcastSearchEpisodeKey) =
        FoundEpisodeViewModelImpl(key, SavedStateHandle(), directory, podcasts, playback, network).also {
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.wheel.collect() }
        }

    private fun EpisodeViewModel.content() = (uiState.value as ListUiState.Loaded).value

    @Test
    fun theEpisode_isFoundInItsShowsFeedByGuid_withPlay() = runTest {
        // The directory's enclosure URL may differ from the feed's: the GUID finds it.
        val content = observedViewModel(PodcastSearchEpisodeKey(show.toKey(), "g2", "https://other/2.mp3")).content()

        assertEquals(foundEpisodeContent(show, battery), content)
        assertEquals(listOf(EpisodeRow.Play), content.rows)
    }

    @Test
    fun anEpisodeWithNoGuid_isFoundByItsEnclosure() = runTest {
        val content = observedViewModel(episodeKeyOf(show, noGuid)).content()

        assertEquals("The Daily Stranger", content.showTitle)
        assertEquals(foundEpisodeContent(show, noGuid), content)
    }

    @Test
    fun anEpisodeTheFeedNoLongerLists_saysSo() = runTest {
        val viewModel = observedViewModel(PodcastSearchEpisodeKey(show.toKey(), "gone", "https://e.com/gone.mp3"))

        assertEquals(ListUiState.NotInFeed, viewModel.uiState.value)
    }

    @Test
    fun noConnection_saysSo_andTheEpisodeComesBackWithIt() = runTest {
        directory.connected.value = false
        val viewModel = observedViewModel(episodeKeyOf(show, battery))

        assertEquals(ListUiState.NoConnection, viewModel.uiState.value)

        directory.connected.value = true

        assertEquals(listOf(EpisodeRow.Play), viewModel.content().rows)
    }

    @Test
    fun whileEpisodesNeedWifi_offersNeedsWifi_whichOpensTheSetting() = runTest {
        network.needsWifi.value = true
        val viewModel = observedViewModel(episodeKeyOf(show, battery))
        var allowed = 0

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = { allowed++ })

        assertEquals(listOf(EpisodeRow.NeedsWifi), viewModel.content().rows)
        assertEquals(1, allowed)
        assertEquals(emptyList<FakePlayback.PlayCall>(), playback.playCalls)
    }

    @Test
    fun play_addsTheEpisodeAsTheFeedHasIt_thenPlaysItFromTheStart_andOpensNowPlaying() = runTest {
        val viewModel = observedViewModel(PodcastSearchEpisodeKey(show.toKey(), "g2", "https://other/2.mp3"))
        var playing = 0

        viewModel.onCenter(onPlaying = { playing++ }, onAllowMobileData = {})

        assertEquals(listOf(show to battery), podcasts.added)
        val id = podcasts.addedEpisodeId
        assertEquals(
            listOf(FakePlayback.PlayCall(PlaybackSource.Episode(id), PlaybackItemId.Episode(id), resume = false)),
            playback.playCalls,
        )
        assertEquals(1, playing)
    }

    @Test
    fun anEpisodeThatCantBeAdded_isNotPlayed_andSaysSo() = runTest {
        podcasts.setWriteError(StorageError.Unavailable)
        val viewModel = observedViewModel(episodeKeyOf(show, battery))

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = {})

        assertEquals(EpisodePlayFailure.EpisodeUnavailable, viewModel.playFailure.value)
        assertEquals(emptyList<FakePlayback.PlayCall>(), playback.playCalls)
    }
}
