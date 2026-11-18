package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.SavedStateHandle
import arrow.core.left
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.testing.FakeEpisodeNetwork
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EpisodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val podcasts = FakePodcastsRepository().apply { fillWithSamples() }
    private val playback = FakePlayback()
    private val network = FakeEpisodeNetwork()

    private fun TestScope.observedViewModel(episode: Episode, savedState: SavedStateHandle = SavedStateHandle()) =
        EpisodeViewModelImpl(episode.id.value, savedState, podcasts, playback, network).also {
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.wheel.collect() }
        }

    private fun EpisodeViewModel.content() = (uiState.value as ListUiState.Loaded).value

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        val viewModel =
            EpisodeViewModelImpl(PodcastSamples.unplayed.id.value, SavedStateHandle(), podcasts, playback, network)

        assertEquals(ListUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun unplayed_offersPlay_withNoTimeLeft() = runTest {
        val content = observedViewModel(PodcastSamples.unplayed).content()

        assertEquals(listOf(EpisodeRow.Play), content.rows)
        assertEquals(EpisodeInfo(PodcastSamples.unplayed.publishedAt, 32.minutes, left = null), content.info)
    }

    @Test
    fun whileEpisodesNeedWifi_anEpisodeWithNoDownload_offersNeedsWifi_andPlayComesBack() = runTest {
        network.needsWifi.value = true
        val viewModel = observedViewModel(PodcastSamples.unplayed)

        assertEquals(listOf(EpisodeRow.NeedsWifi), viewModel.content().rows)

        network.needsWifi.value = false

        assertEquals(listOf(EpisodeRow.Play), viewModel.content().rows)
    }

    @Test
    fun whileEpisodesNeedWifi_anEpisodePartlyDownloaded_keepsItsRows() = runTest {
        network.needsWifi.value = true

        assertEquals(listOf(EpisodeRow.Play), observedViewModel(PodcastSamples.downloading).content().rows)
    }

    @Test
    fun centerOnNeedsWifi_opensTheSettings_andPlaysNothing() = runTest {
        network.needsWifi.value = true
        val viewModel = observedViewModel(PodcastSamples.unplayed)
        var allowed = 0

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = { allowed++ })

        assertEquals(1, allowed)
        assertEquals(emptyList<FakePlayback.PlayCall>(), playback.playCalls)
    }

    @Test
    fun partlyPlayed_offersResumeAndPlayFromStart_withTheTimeLeft() = runTest {
        val content = observedViewModel(PodcastSamples.partlyPlayed).content()

        assertEquals(listOf(EpisodeRow.Resume, EpisodeRow.PlayFromStart), content.rows)
        assertEquals(20.minutes, content.info.left)
    }

    @Test
    fun played_offersPlay_fromTheStart_withNoTimeLeft() = runTest {
        val content = observedViewModel(PodcastSamples.played).content()

        assertEquals(listOf(EpisodeRow.Play), content.rows)
        assertNull(content.info.left)
    }

    @Test
    fun title_isTheShows() = runTest {
        assertEquals("The Daily", observedViewModel(PodcastSamples.partlyPlayed).content().showTitle)
    }

    @Test
    fun longDescription_isKeptWhole() = runTest {
        val long = List(200) { "A sentence of the description, number $it." }.joinToString(" ")
        val episode = PodcastSamples.partlyPlayed.copy(description = long)
        podcasts.episodes = podcasts.episodes.map { if (it.id == episode.id) episode else it }

        assertEquals(long, observedViewModel(episode).content().description)
    }

    @Test
    fun emptyOrBlankDescription_isNone() = runTest {
        val empty = PodcastSamples.unplayed.copy(description = "")
        val blank = PodcastSamples.played.copy(description = "  \n ")
        podcasts.episodes = podcasts.episodes.map {
            when (it.id) {
                empty.id -> empty
                blank.id -> blank
                else -> it
            }
        }

        assertNull(observedViewModel(empty).content().description)
        assertNull(observedViewModel(blank).content().description)
    }

    @Test
    fun episodeThatIsntInTheLibrary_isGone() = runTest {
        podcasts.episodes = podcasts.episodes.filter { it.id != PodcastSamples.unplayed.id }

        assertEquals(ListUiState.Gone, observedViewModel(PodcastSamples.unplayed).uiState.value)
    }

    @Test
    fun podcastsThatCantBeRead_failed() = runTest {
        podcasts.setReadError(StorageError.Unavailable)

        assertEquals(ListUiState.Failed, observedViewModel(PodcastSamples.unplayed).uiState.value)
    }

    @Test
    fun turningForward_movesToTheLastRow_thenScrollsTheDescription_andStopsAtItsEnd() = runTest {
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed)
        viewModel.onScrollableLinesChange(3)

        viewModel.onTurn(1)
        assertEquals(EpisodeWheel(row = 1, lines = 0), viewModel.wheel.value)

        viewModel.onTurn(2)
        assertEquals(EpisodeWheel(row = 1, lines = 2), viewModel.wheel.value)

        viewModel.onTurn(5)
        assertEquals(EpisodeWheel(row = 1, lines = 3), viewModel.wheel.value)
    }

    @Test
    fun turningBack_scrollsTheDescriptionUp_thenReturnsToTheRows() = runTest {
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed)
        viewModel.onScrollableLinesChange(3)
        viewModel.onTurn(4)

        viewModel.onTurn(-2)
        assertEquals(EpisodeWheel(row = 1, lines = 1), viewModel.wheel.value)

        viewModel.onTurn(-2)
        assertEquals(EpisodeWheel(row = 0, lines = 0), viewModel.wheel.value)

        viewModel.onTurn(-3)
        assertEquals(EpisodeWheel(row = 0, lines = 0), viewModel.wheel.value)
    }

    @Test
    fun oneTurn_canCrossFromTheRowsIntoTheDescription_andBack() = runTest {
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed)
        viewModel.onScrollableLinesChange(10)

        viewModel.onTurn(4)
        assertEquals(EpisodeWheel(row = 1, lines = 3), viewModel.wheel.value)

        viewModel.onTurn(-4)
        assertEquals(EpisodeWheel(row = 0, lines = 0), viewModel.wheel.value)
    }

    @Test
    fun withOneRow_turningScrollsTheDescriptionAtOnce() = runTest {
        val viewModel = observedViewModel(PodcastSamples.unplayed)
        viewModel.onScrollableLinesChange(5)

        viewModel.onTurn(2)

        assertEquals(EpisodeWheel(row = 0, lines = 2), viewModel.wheel.value)
    }

    @Test
    fun descriptionThatFits_isntScrolled() = runTest {
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed)
        viewModel.onScrollableLinesChange(0)

        viewModel.onTurn(3)

        assertEquals(EpisodeWheel(row = 1, lines = 0), viewModel.wheel.value)
    }

    @Test
    fun fewerLinesToScroll_keepTheDescriptionAtItsEnd() = runTest {
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed)
        viewModel.onScrollableLinesChange(8)
        viewModel.onTurn(9)

        viewModel.onScrollableLinesChange(5)

        assertEquals(EpisodeWheel(row = 1, lines = 5), viewModel.wheel.value)
    }

    @Test
    fun wheel_isSavedWithTheEntry() = runTest {
        val savedState = SavedStateHandle()
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed, savedState)
        viewModel.onScrollableLinesChange(6)
        viewModel.onTurn(3)

        val restored = observedViewModel(PodcastSamples.partlyPlayed, savedState)

        assertEquals(EpisodeWheel(row = 1, lines = 2), restored.wheel.value)
    }

    // Playing (ADR-031, Episodes in playback)

    @Test
    fun centerOnPlay_playsTheEpisodeAloneFromTheStart_andThenOpensNowPlaying() = runTest {
        val viewModel = observedViewModel(PodcastSamples.unplayed)
        var playing = 0

        viewModel.onCenter(onPlaying = { playing++ }, onAllowMobileData = {})

        val id = PodcastSamples.unplayed.id
        assertEquals(
            listOf(FakePlayback.PlayCall(PlaybackSource.Episode(id), PlaybackItemId.Episode(id), resume = false)),
            playback.playCalls,
        )
        assertEquals(1, playing)
    }

    @Test
    fun centerOnResume_resumes_andOnPlayFromStart_startsOver() = runTest {
        val viewModel = observedViewModel(PodcastSamples.partlyPlayed)

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = {})
        viewModel.onTurn(1)
        viewModel.onCenter(onPlaying = {}, onAllowMobileData = {})

        assertEquals(listOf(true, false), playback.playCalls.map { it.resume })
    }

    @Test
    fun aPlayThatFails_saysWhy_andOpensNothing() = runTest {
        playback.playResult = PlaybackError.Podcasts(PodcastError.EpisodeNotFound(PodcastSamples.unplayed.id)).left()
        val viewModel = observedViewModel(PodcastSamples.unplayed)
        var playing = 0

        viewModel.onCenter(onPlaying = { playing++ }, onAllowMobileData = {})

        assertEquals(0, playing)
        assertEquals(EpisodePlayFailure.EpisodeUnavailable, viewModel.playFailure.value)
        viewModel.onPlayFailureShown()
        assertNull(viewModel.playFailure.value)
    }

    @Test
    fun aPlayerThatCantBeReached_saysSo() = runTest {
        playback.playResult = PlaybackError.PlayerUnavailable.left()
        val viewModel = observedViewModel(PodcastSamples.unplayed)

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = {})

        assertEquals(EpisodePlayFailure.PlayerUnavailable, viewModel.playFailure.value)
    }

    @Test
    fun noRoomForTheDownload_saysSo() = runTest {
        playback.playResult = PlaybackError.StorageFull.left()
        val viewModel = observedViewModel(PodcastSamples.unplayed)

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = {})

        assertEquals(EpisodePlayFailure.StorageFull, viewModel.playFailure.value)
    }

    @Test
    fun mobileDataOff_saysSo() = runTest {
        playback.playResult = PlaybackError.MobileDataOff.left()
        val viewModel = observedViewModel(PodcastSamples.unplayed)

        viewModel.onCenter(onPlaying = {}, onAllowMobileData = {})

        assertEquals(EpisodePlayFailure.MobileDataOff, viewModel.playFailure.value)
    }
}
