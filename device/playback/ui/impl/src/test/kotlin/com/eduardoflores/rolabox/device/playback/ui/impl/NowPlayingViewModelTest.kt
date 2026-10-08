package com.eduardoflores.rolabox.device.playback.ui.impl

import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSong
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlayback()

    private fun TestScope.observedViewModel() = NowPlayingViewModelImpl(playback).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
    }

    @Test
    fun beforeAnythingIsObserved_isLoading() {
        assertEquals(NowPlayingUiState.Loading, NowPlayingViewModelImpl(playback).uiState.value)
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

    private companion object {
        const val LONG_TITLE = "The Unreasonably Long Name of a Song That Will Never Fit on a Small Display"

        fun song(id: Long, title: String, duration: kotlin.time.Duration = 3.minutes + 41.seconds) =
            PlaybackSong(SongId(id), title, "Maren Holt", "Paper Satellites", duration)

        val Queue = listOf(song(1, "Dial Tone"), song(2, "Coastline"), song(3, "Glasshouse"))
    }
}
