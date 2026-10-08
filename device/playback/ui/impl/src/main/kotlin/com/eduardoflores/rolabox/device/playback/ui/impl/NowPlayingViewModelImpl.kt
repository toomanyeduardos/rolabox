package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Observes the playback state and owns none of it (ADR-018, rule 10): the screen can be popped while the
 * music continues, and the next one starts from the state again.
 */
@HiltViewModel
internal class NowPlayingViewModelImpl @Inject constructor(playbackState: PlaybackState) : NowPlayingViewModel() {
    override val uiState: StateFlow<NowPlayingUiState> = combine(
        playbackState.currentSong,
        playbackState.position,
        playbackState.queueIndex,
        playbackState.queue,
    ) { song, position, index, queue ->
        if (song == null || index == null) {
            NowPlayingUiState.NothingLoaded
        } else {
            NowPlayingUiState.Playing(
                title = song.title,
                artist = song.artist,
                album = song.album,
                number = index + 1,
                count = queue.size,
                position = position.coerceIn(Duration.ZERO, song.duration.coerceAtLeast(Duration.ZERO)),
                duration = song.duration,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NowPlayingUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
