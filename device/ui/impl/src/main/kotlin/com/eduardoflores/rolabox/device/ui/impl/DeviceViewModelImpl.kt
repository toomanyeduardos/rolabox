package com.eduardoflores.rolabox.device.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Observes the playback state and acts on it, and owns none of it (ADR-018, rule 10). The ends of the
 * queue and nothing being loaded are the controller's to handle, so the buttons only ask.
 */
@HiltViewModel
internal class DeviceViewModelImpl @Inject constructor(
    private val playbackState: PlaybackState,
    private val playbackController: PlaybackController,
    userDataRepository: UserDataRepository,
) : DeviceViewModel() {
    override val hasLoadedSong: StateFlow<Boolean> = playbackState.currentSong
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    override val isPlaying: StateFlow<Boolean> = playbackState.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    override val mainMenuRows: StateFlow<Map<MainMenuRow, MainMenuRowVisibility>> = userDataRepository.observeUserData()
        .map { result -> result.getOrNull()?.mainMenuRows ?: AllShown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AllShown)

    private val playFailureState = MutableStateFlow<PlayFailure?>(null)
    override val playFailure: StateFlow<PlayFailure?> = playFailureState.asStateFlow()

    override fun onSongClick(source: PlaybackSource, songId: SongId, onPlaying: () -> Unit) {
        viewModelScope.launch {
            playbackController.play(source, songId).fold(
                ifLeft = { error -> playFailureState.value = error.toPlayFailure() },
                ifRight = { onPlaying() },
            )
        }
    }

    override fun onPlayFailureShown() {
        playFailureState.value = null
    }

    override fun onNext() {
        viewModelScope.launch { playbackController.next() }
    }

    override fun onPrevious() {
        viewModelScope.launch { playbackController.previous() }
    }

    override fun onPlayPause() {
        viewModelScope.launch {
            if (playbackState.isPlaying.first()) playbackController.pause() else playbackController.resume()
        }
    }

    override fun onSeekStart(direction: SeekDirection) {
        viewModelScope.launch { playbackController.startSeeking(direction) }
    }

    override fun onSeekEnd() {
        viewModelScope.launch { playbackController.stopSeeking() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private val AllShown = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN }

private fun PlaybackError.toPlayFailure(): PlayFailure = when (this) {
    is PlaybackError.Library -> PlayFailure.LibraryUnavailable
    is PlaybackError.SongNotInSource -> PlayFailure.SongNotInList
}
