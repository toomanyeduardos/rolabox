package com.eduardoflores.rolabox.device.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Observes the playback state and acts on it, and owns none of it (ADR-018, rule 10). The ends of the
 * queue and nothing being loaded are the controller's to handle, so ⏮ and ⏭ only ask.
 */
@HiltViewModel
internal class DeviceViewModelImpl @Inject constructor(
    playbackState: PlaybackState,
    private val playbackController: PlaybackController,
) : DeviceViewModel() {
    override val hasLoadedSong: StateFlow<Boolean> = playbackState.currentSong
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    override fun onSongClick(source: PlaybackSource, songId: SongId, onPlaying: () -> Unit) {
        viewModelScope.launch {
            playbackController.play(source, songId).onRight { onPlaying() }
        }
    }

    override fun onNext() {
        viewModelScope.launch { playbackController.next() }
    }

    override fun onPrevious() {
        viewModelScope.launch { playbackController.previous() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
