package com.eduardoflores.rolabox.device.playback.ui.impl

import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSong
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlayback()

    private fun TestScope.observedViewModel() = NowPlayingViewModelImpl(playback, playback).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(NowPlayingUiState.Loading, NowPlayingViewModelImpl(playback, playback).uiState.value)
    }

    @Test
    fun nothingLoaded_isNothingLoaded() = runTest {
        assertEquals(NowPlayingUiState.NothingLoaded, observedViewModel().uiState.value)
    }

    @Test
    fun aSong_isItsNamesItsPlaceInTheQueueAndItsPosition() = runTest {
        val viewModel = observedViewModel()

        playback.load(Queue, index = 2)
        playback.positionAt(1.minutes + 12.seconds)

        assertEquals(
            NowPlayingUiState.Playing(
                title = "Glasshouse",
                artist = "Maren Holt",
                album = "Paper Satellites",
                number = 3,
                count = 3,
                position = 1.minutes + 12.seconds,
                duration = 3.minutes + 41.seconds,
                volume = 50,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun aLONG_TITLE_isKeptWhole() = runTest {
        val viewModel = observedViewModel()

        playback.load(listOf(song(1, LONG_TITLE)), index = 0)

        assertEquals(LONG_TITLE, (viewModel.uiState.value as NowPlayingUiState.Playing).title)
    }

    @Test
    fun whenTheSongChanges_theStateFollowsAtItsStart() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)
        playback.positionAt(30.seconds)

        playback.load(Queue, index = 1)

        val state = viewModel.uiState.value as NowPlayingUiState.Playing
        assertEquals("Coastline", state.title)
        assertEquals(2, state.number)
        assertEquals(0.seconds, state.position)
    }

    @Test
    fun thePosition_isKeptWithinTheSong() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)

        playback.positionAt(10.minutes)

        assertEquals(1f, (viewModel.uiState.value as NowPlayingUiState.Playing).progress)
        assertEquals(Queue[0].duration, (viewModel.uiState.value as NowPlayingUiState.Playing).position)
    }

    @Test
    fun whenTheQueueEnds_isNothingLoaded() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)

        playback.unload()

        assertEquals(NowPlayingUiState.NothingLoaded, viewModel.uiState.value)
    }

    @Test
    fun theProgress_isThePositionOverTheDuration() {
        val playing = NowPlayingUiState.Playing("t", "a", "b", 1, 1, position = 1.minutes, duration = 4.minutes)

        assertEquals(0.25f, playing.progress)
    }

    @Test
    fun aSongWithoutLength_isAtTheStart() {
        val playing = NowPlayingUiState.Playing("t", "a", "b", 1, 1, position = 0.seconds, duration = 0.seconds)

        assertEquals(0f, playing.progress)
    }

    private fun TestScope.scrubbingViewModel(position: Duration = 1.minutes) = observedViewModel().also {
        playback.load(Queue, index = 2)
        playback.positionAt(position)
        it.onCenter()
    }

    private fun NowPlayingViewModel.playing() = uiState.value as NowPlayingUiState.Playing

    @Test
    fun center_entersScrub() = runTest {
        val viewModel = scrubbingViewModel()

        assertEquals(NowPlayingMode.Scrub, viewModel.playing().mode)
    }

    @Test
    fun center_withNothingLoaded_doesNothing() = runTest {
        val viewModel = observedViewModel()

        viewModel.onCenter()

        assertEquals(NowPlayingUiState.NothingLoaded, viewModel.uiState.value)
    }

    @Test
    fun inScrub_aTurnMovesTheMarkerOneStepPerWheelStep_andDoesNotSeek() = runTest {
        val viewModel = scrubbingViewModel()

        viewModel.onTurn(3)

        assertEquals(1.minutes + ScrubStep * 3, viewModel.playing().position)
        assertEquals(emptyList<Duration>(), playback.seekCalls)
        assertEquals(emptyList<Int>(), playback.volumeCalls)
        assertEquals(NowPlayingMode.Scrub, viewModel.playing().mode)
    }

    @Test
    fun inScrub_aTurnCounterClockwiseMovesTheMarkerBackward() = runTest {
        val viewModel = scrubbingViewModel()

        viewModel.onTurn(-2)

        assertEquals(1.minutes - ScrubStep * 2, viewModel.playing().position)
    }

    @Test
    fun inScrub_turnsAddUp() = runTest {
        val viewModel = scrubbingViewModel()

        viewModel.onTurn(3)
        viewModel.onTurn(-1)

        assertEquals(1.minutes + ScrubStep * 2, viewModel.playing().position)
    }

    @Test
    fun inScrub_beforeATurn_theBarFollowsTheSong() = runTest {
        val viewModel = scrubbingViewModel()

        playback.positionAt(1.minutes + 10.seconds)

        assertEquals(1.minutes + 10.seconds, viewModel.playing().position)
    }

    @Test
    fun inScrub_afterATurn_theMarkerStaysWhileTheSongPlays() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(1)

        playback.positionAt(1.minutes + 30.seconds)

        assertEquals(1.minutes + ScrubStep, viewModel.playing().position)
    }

    @Test
    fun inScrub_theMarkerStopsAtTheStart() = runTest {
        val viewModel = scrubbingViewModel(position = 2.seconds)

        viewModel.onTurn(-10)

        assertEquals(Duration.ZERO, viewModel.playing().position)
    }

    @Test
    fun inScrub_theMarkerStopsAtTheEnd() = runTest {
        val viewModel = scrubbingViewModel(position = Queue[2].duration - 1.seconds)

        viewModel.onTurn(10)

        assertEquals(Queue[2].duration, viewModel.playing().position)
    }

    @Test
    fun centerAgain_seeksToTheMarkerAndLeavesScrub() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(4)

        viewModel.onCenter()

        assertEquals(listOf(1.minutes + ScrubStep * 4), playback.seekCalls)
        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
    }

    @Test
    fun centerAgain_isNotAcceptedASecondTimeByTheTimer() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(4)
        viewModel.onCenter()

        advanceTimeBy(ScrubIdle + 1.milliseconds)

        assertEquals(listOf(1.minutes + ScrubStep * 4), playback.seekCalls)
    }

    @Test
    fun centerAgain_withoutATurn_leavesScrubAndDoesNotSeek() = runTest {
        val viewModel = scrubbingViewModel()

        viewModel.onCenter()

        assertEquals(emptyList<Duration>(), playback.seekCalls)
        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
    }

    @Test
    fun scrub_isAcceptedAfterThreeSecondsWithoutATurn() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(1)

        advanceTimeBy(ScrubIdle - 1.milliseconds)
        assertEquals(NowPlayingMode.Scrub, viewModel.playing().mode)
        assertEquals(emptyList<Duration>(), playback.seekCalls)
        advanceTimeBy(2.milliseconds)

        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
        assertEquals(listOf(1.minutes + ScrubStep), playback.seekCalls)
    }

    @Test
    fun scrubWithoutATurn_endsAfterThreeSecondsFromCenter_andDoesNotSeek() = runTest {
        val viewModel = scrubbingViewModel()

        advanceTimeBy(ScrubIdle + 1.milliseconds)

        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
        assertEquals(emptyList<Duration>(), playback.seekCalls)
    }

    @Test
    fun aTurn_restartsTheScrubTimer() = runTest {
        val viewModel = scrubbingViewModel()
        advanceTimeBy(ScrubIdle - 1.seconds)

        viewModel.onTurn(1)
        advanceTimeBy(ScrubIdle - 1.seconds)

        assertEquals(NowPlayingMode.Scrub, viewModel.playing().mode)
        advanceTimeBy(2.seconds)
        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
    }

    @Test
    fun menu_leavesScrubWithoutSeeking_andTheBarShowsTheSongAgain() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(20)

        viewModel.onMenu()
        advanceTimeBy(ScrubIdle + 1.milliseconds)

        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
        assertEquals(1.minutes, viewModel.playing().position)
        assertEquals(emptyList<Duration>(), playback.seekCalls)
    }

    @Test
    fun menu_outsideScrub_doesNothing() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)
        viewModel.onTurn(1)

        viewModel.onMenu()

        assertEquals(NowPlayingMode.Volume, viewModel.playing().mode)
    }

    @Test
    fun aNewScrub_startsFromTheSong_notFromTheLastMarker() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(20)
        viewModel.onMenu()

        viewModel.onCenter()
        viewModel.onTurn(1)

        assertEquals(1.minutes + ScrubStep, viewModel.playing().position)
    }

    @Test
    fun theSongChangesInScrub_scrubEnds_andTheMarkerIsNotSought() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(20)

        playback.load(Queue, index = 1)

        assertEquals(emptyList<Duration>(), playback.seekCalls)
        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
        assertEquals(Duration.ZERO, viewModel.playing().position)
    }

    @Test
    fun theSongChangesInScrub_theScrubTimerDoesNotSeekLater() = runTest {
        val viewModel = scrubbingViewModel()
        viewModel.onTurn(20)
        playback.load(Queue, index = 1)

        advanceTimeBy(ScrubIdle + 1.seconds)

        assertEquals(emptyList<Duration>(), playback.seekCalls)
        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
    }

    @Test
    fun outsideScrub_aTurnChangesTheVolumeAndShowsIt() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)

        viewModel.onTurn(12)
        playback.volumeAt(playback.volumeCalls.last())

        assertEquals(listOf(50 + VOLUME_STEP * 12), playback.volumeCalls)
        assertEquals(emptyList<Duration>(), playback.seekCalls)
        assertEquals(NowPlayingMode.Volume, viewModel.playing().mode)
        assertEquals(62, viewModel.playing().volume)
    }

    @Test
    fun theVolume_stopsAtBothEnds() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)

        viewModel.onTurn(500)
        playback.volumeAt(0)
        viewModel.onTurn(-500)

        assertEquals(listOf(100, 0), playback.volumeCalls)
    }

    @Test
    fun withNothingLoaded_aTurnChangesNothing() = runTest {
        val viewModel = observedViewModel()

        viewModel.onTurn(1)

        assertEquals(emptyList<Int>(), playback.volumeCalls)
        assertEquals(NowPlayingUiState.NothingLoaded, viewModel.uiState.value)
    }

    @Test
    fun theVolume_returnsToTheTimeAfterTwoSeconds() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)
        viewModel.onTurn(1)

        advanceTimeBy(VolumeIdle - 1.milliseconds)
        assertEquals(NowPlayingMode.Volume, viewModel.playing().mode)
        advanceTimeBy(2.milliseconds)

        assertEquals(NowPlayingMode.Time, viewModel.playing().mode)
    }

    @Test
    fun aTurn_restartsTheVolumeTimer() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)
        viewModel.onTurn(1)
        advanceTimeBy(VolumeIdle - 1.seconds)

        viewModel.onTurn(1)
        advanceTimeBy(VolumeIdle - 1.seconds)

        assertEquals(NowPlayingMode.Volume, viewModel.playing().mode)
    }

    @Test
    fun centerWhileTheVolumeShows_entersScrubInTime() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 0)
        viewModel.onTurn(1)

        viewModel.onCenter()
        advanceTimeBy(VolumeIdle + 1.milliseconds)

        // The volume timer is gone: only scrub's own timer is left, so scrub is still on.
        assertEquals(NowPlayingMode.Scrub, viewModel.playing().mode)
    }

    @Test
    fun theVolumeBar_isTheLevelOverOneHundred() {
        val playing = NowPlayingUiState.Playing("t", "a", "b", 1, 1, 1.minutes, 4.minutes, volume = 62)

        assertEquals(0.62f, playing.volumeFraction)
    }

    private companion object {
        const val LONG_TITLE = "The Unreasonably Long Name of a Song That Will Never Fit on a Small Display"

        fun song(id: Long, title: String, duration: Duration = 3.minutes + 41.seconds) =
            PlaybackSong(SongId(id), title, "Maren Holt", "Paper Satellites", duration)

        val Queue = listOf(song(1, "Dial Tone"), song(2, "Coastline"), song(3, "Glasshouse"))
    }
}
