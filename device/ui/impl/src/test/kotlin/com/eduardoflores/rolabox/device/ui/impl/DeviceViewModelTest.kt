package com.eduardoflores.rolabox.device.ui.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.designsystem.wheel.HoldState
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlayback()
    private val userData = FakeUserDataRepository()

    private fun TestScope.observedViewModel() = DeviceViewModelImpl(playback, playback, userData).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.hasLoadedSong.collect() }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.isPlaying.collect() }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.mainMenuRows.collect() }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.clicks.collect() }
    }

    @Test
    fun theWheel_followsTheClickerSetting() = runTest {
        val viewModel = observedViewModel()
        assertTrue(viewModel.clicks.value)

        userData.setClicker(Clicker.OFF)
        assertFalse(viewModel.clicks.value)

        userData.setClicker(Clicker.ON)
        assertTrue(viewModel.clicks.value)
    }

    @Test
    fun aClickerThatCannotBeRead_clicks() = runTest {
        userData.setReadError(StorageError.Corrupted)

        assertTrue(observedViewModel().clicks.value)
    }

    @Test
    fun theMainMenu_followsTheMainMenuSettings() = runTest {
        val viewModel = observedViewModel()
        assertEquals(AllShown, viewModel.mainMenuRows.value)

        userData.setMainMenuRowVisibility(MainMenuRow.PODCASTS, MainMenuRowVisibility.HIDDEN)
        assertEquals(AllShown + (MainMenuRow.PODCASTS to MainMenuRowVisibility.HIDDEN), viewModel.mainMenuRows.value)
        assertEquals(
            listOf(MenuRow.Music, MenuRow.Audiobooks, MenuRow.ShuffleSongs, MenuRow.Settings),
            shownMenuRows(nowPlayingShown = false, visibility = viewModel.mainMenuRows.value),
        )

        userData.setMainMenuRowVisibility(MainMenuRow.PODCASTS, MainMenuRowVisibility.SHOWN)
        assertEquals(AllShown, viewModel.mainMenuRows.value)
    }

    @Test
    fun mainMenuPreferencesThatCannotBeRead_showEveryRow() = runTest {
        userData.setReadError(StorageError.Corrupted)

        assertEquals(AllShown, observedViewModel().mainMenuRows.value)
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
    fun isPlaying_followsThePlaybackState() = runTest {
        val viewModel = observedViewModel()
        assertFalse(viewModel.isPlaying.value)

        playback.load(Queue, index = 0)
        playback.playing(true)
        assertTrue(viewModel.isPlaying.value)

        playback.playing(false)
        assertFalse(viewModel.isPlaying.value)
    }

    @Test
    fun aSongClick_playsTheListFromThatSong_andThenReportsIt() = runTest {
        val viewModel = observedViewModel()
        var playing = 0

        viewModel.onSongClick(PlaybackSource.ByAlbum(AlbumId(7)), SongId(3)) { playing++ }

        assertEquals(
            listOf(FakePlayback.PlayCall(PlaybackSource.ByAlbum(AlbumId(7)), PlaybackItemId.Song(SongId(3)))),
            playback.playCalls,
        )
        assertEquals(1, playing)
    }

    @Test
    fun aSongClickThatFails_doesNotReportPlaying_andSaysTheSongIsNotInTheList() = runTest {
        val viewModel = observedViewModel()
        val source = PlaybackSource.ByAlbum(AlbumId(7))
        playback.playResult = PlaybackError.ItemNotInSource(PlaybackItemId.Song(SongId(3)), source).left()
        var playing = 0

        viewModel.onSongClick(source, SongId(3)) { playing++ }

        assertEquals(1, playback.playCalls.size)
        assertEquals(0, playing)
        assertEquals(PlayFailure.SongNotInList, viewModel.playFailure.value)
    }

    @Test
    fun aSongClickWhoseListCannotBeRead_saysTheLibraryIsUnavailable() = runTest {
        val viewModel = observedViewModel()
        playback.playResult = PlaybackError.Library(LibraryError.AlbumNotFound(AlbumId(7))).left()

        viewModel.onSongClick(PlaybackSource.ByAlbum(AlbumId(7)), SongId(3)) {}

        assertEquals(PlayFailure.LibraryUnavailable, viewModel.playFailure.value)
    }

    @Test
    fun aSongClickThatCannotReachThePlayer_saysThePlayerIsUnavailable() = runTest {
        val viewModel = observedViewModel()
        playback.playResult = PlaybackError.PlayerUnavailable.left()

        viewModel.onSongClick(PlaybackSource.ByAlbum(AlbumId(7)), SongId(3)) {}

        assertEquals(PlayFailure.PlayerUnavailable, viewModel.playFailure.value)
    }

    @Test
    fun aFailureThatWasShown_isNotShownAgain_andASongThatPlaysHasNone() = runTest {
        val viewModel = observedViewModel()
        val source = PlaybackSource.ByAlbum(AlbumId(7))
        playback.playResult = PlaybackError.ItemNotInSource(PlaybackItemId.Song(SongId(3)), source).left()
        viewModel.onSongClick(source, SongId(3)) {}

        viewModel.onPlayFailureShown()
        assertNull(viewModel.playFailure.value)

        playback.playResult = Unit.right()
        viewModel.onSongClick(source, SongId(3)) {}
        assertNull(viewModel.playFailure.value)
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

    @Test
    fun playPause_onAPlayingSong_pausesIt() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)

        viewModel.onPlayPause()

        assertEquals(1, playback.pauseCalls)
        assertEquals(0, playback.resumeCalls)
    }

    @Test
    fun playPause_onAPausedSong_playsItFromWhereItWas() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)
        playback.playing(false)

        viewModel.onPlayPause()

        assertEquals(1, playback.resumeCalls)
        assertEquals(0, playback.pauseCalls)
        assertEquals(emptyList<FakePlayback.PlayCall>(), playback.playCalls)
    }

    // Nothing is playing, so the handler asks to resume, and with nothing loaded that is the controller's to
    // ignore: InMemoryPlayerTest covers it. What the handler never does is load something.
    @Test
    fun playPause_withNothingLoaded_neitherPausesNorLoadsAnything() = runTest {
        val viewModel = observedViewModel()

        viewModel.onPlayPause()

        assertEquals(0, playback.pauseCalls)
        assertEquals(emptyList<FakePlayback.PlayCall>(), playback.playCalls)
        assertFalse(viewModel.hasLoadedSong.value)
    }

    @Test
    fun aSeekThatStartsAndEnds_asksThePlaybackForBoth() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)

        viewModel.onSeekStart(SeekDirection.Backward)
        assertEquals(listOf(SeekDirection.Backward), playback.startSeekingCalls)
        assertEquals(0, playback.stopSeekingCalls)

        viewModel.onSeekEnd()
        assertEquals(1, playback.stopSeekingCalls)
    }

    @Test
    fun thePlaybackButtons_eachDoWhatTheControlTableSays() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)

        viewModel.onPlaybackEvent(WheelEvent.Next)
        viewModel.onPlaybackEvent(WheelEvent.Previous)
        viewModel.onPlaybackEvent(WheelEvent.PlayPause)
        viewModel.onPlaybackEvent(WheelEvent.HoldPrevious(HoldState.Held))
        viewModel.onPlaybackEvent(WheelEvent.HoldPrevious(HoldState.Released))
        viewModel.onPlaybackEvent(WheelEvent.HoldNext(HoldState.Held))
        viewModel.onPlaybackEvent(WheelEvent.HoldNext(HoldState.Released))

        assertEquals(1, playback.nextCalls)
        assertEquals(1, playback.previousCalls)
        assertEquals(1, playback.pauseCalls)
        assertEquals(listOf(SeekDirection.Backward, SeekDirection.Forward), playback.startSeekingCalls)
        assertEquals(2, playback.stopSeekingCalls)
    }

    @Test
    fun theWheelEventsThatAreNotPlaybackButtons_doNothingToPlayback() = runTest {
        val viewModel = observedViewModel()
        playback.load(Queue, index = 1)

        listOf(WheelEvent.Turn(1), WheelEvent.Center, WheelEvent.Menu, WheelEvent.HoldMenu)
            .forEach(viewModel::onPlaybackEvent)

        assertEquals(0, playback.nextCalls + playback.previousCalls + playback.pauseCalls + playback.resumeCalls)
        assertEquals(emptyList<SeekDirection>(), playback.startSeekingCalls)
        assertEquals(0, playback.stopSeekingCalls)
    }
}

private val Queue = (1L..3L).map {
    PlaybackItem(PlaybackItemId.Song(SongId(it)), "Song $it", "Artist", "Album", duration = 3.minutes)
}

private val AllShown = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN }
