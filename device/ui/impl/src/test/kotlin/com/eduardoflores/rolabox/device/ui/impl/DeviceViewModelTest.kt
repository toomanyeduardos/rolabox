package com.eduardoflores.rolabox.device.ui.impl

import arrow.core.left
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackSong
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlayback()

    private fun TestScope.observedViewModel() = DeviceViewModelImpl(playback, playback).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.hasLoadedSong.collect() }
    }

    @Test
    fun nothingLoaded_hasNoLoadedSong() = runTest {
        assertFalse(observedViewModel().hasLoadedSong.value)
    }

    @Test
    fun aLoadedSong_isLoaded_andIsNotOnceTheQueueEnds() = runTest {
        val viewModel = observedViewModel()

        playback.load(Queue, index = 0)
        assertTrue(viewModel.hasLoadedSong.value)

        playback.unload()
        assertFalse(viewModel.hasLoadedSong.value)
    }

    @Test
    fun aSongClick_playsTheListFromThatSong_andThenReportsIt() = runTest {
        val viewModel = observedViewModel()
        var playing = 0

        viewModel.onSongClick(PlaybackSource.ByAlbum(AlbumId(7)), SongId(3)) { playing++ }

        assertEquals(
            listOf(FakePlayback.PlayCall(PlaybackSource.ByAlbum(AlbumId(7)), SongId(3))),
            playback.playCalls,
        )
        assertEquals(1, playing)
    }

    @Test
    fun aSongClickThatFails_doesNotReportPlaying() = runTest {
        val viewModel = observedViewModel()
        val source = PlaybackSource.ByAlbum(AlbumId(7))
        playback.playResult = PlaybackError.SongNotInSource(SongId(3), source).left()
        var playing = 0

        viewModel.onSongClick(source, SongId(3)) { playing++ }

        assertEquals(1, playback.playCalls.size)
        assertEquals(0, playing)
    }

    @Test
    fun next_asksThePlaybackForTheNextSong() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)

        viewModel.onNext()

        assertEquals(1, playback.nextCalls)
        assertEquals(0, playback.previousCalls)
    }

    @Test
    fun previous_asksThePlaybackForThePreviousSong() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)

        viewModel.onPrevious()

        assertEquals(1, playback.previousCalls)
        assertEquals(0, playback.nextCalls)
    }

    // The ends of the queue and nothing being loaded are the controller's: the handlers ask in every case,
    // and InMemoryPlayerTest covers what it does then.
    @Test
    fun nextAndPrevious_atBothEndsAndWithNothingLoaded_stillAskThePlayback() = runTest {
        val viewModel = observedViewModel()

        viewModel.onNext()
        viewModel.onPrevious()
        playback.load(Queue, index = 0)
        viewModel.onPrevious()
        playback.load(Queue, index = Queue.lastIndex)
        viewModel.onNext()

        assertEquals(2, playback.nextCalls)
        assertEquals(2, playback.previousCalls)
    }
}

private val Queue = (1L..3L).map {
    PlaybackSong(SongId(it), "Song $it", "Artist", "Album", duration = 3.minutes)
}
