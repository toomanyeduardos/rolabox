package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far one step of the wheel moves the position in scrub. Tune it once it can be felt. */
internal val ScrubStep: Duration = 1.seconds

/** How much one step of the wheel changes the volume, out of 100. Tune it once it can be felt. */
internal const val VOLUME_STEP = 1

/** How long scrub waits for a turn before it ends on its own (ADR-018, rule 8). */
internal val ScrubIdle: Duration = 3.seconds

/** How long the volume stays on the bar after a turn, before the time returns (ADR-018, rule 8). */
internal val VolumeIdle: Duration = 2.seconds

/**
 * Observes the playback state and owns none of it (ADR-018, rule 10): the screen can be popped while the
 * music continues, and the next one starts from the state again. What it does own is the mode of the
 * screen, which goes back to the time by itself after a pause.
 */
@HiltViewModel
internal class NowPlayingViewModelImpl @Inject constructor(
    private val playbackState: PlaybackState,
    private val playbackController: PlaybackController,
) : NowPlayingViewModel() {
    private val mode = MutableStateFlow(NowPlayingMode.Time)
    private var idleJob: Job? = null

    override val uiState: StateFlow<NowPlayingUiState> = combine(
        playbackState.currentSong,
        playbackState.position,
        playbackState.queueIndex,
        playbackState.queue,
        combine(mode, playbackState.volume, ::Pair),
    ) { song, position, index, queue, (mode, volume) ->
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
                mode = mode,
                volume = volume,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NowPlayingUiState.Loading)

    override fun onTurn(steps: Int) {
        viewModelScope.launch {
            val song = playbackState.currentSong.first() ?: return@launch
            if (mode.value == NowPlayingMode.Scrub) {
                val position = playbackState.position.first() + ScrubStep * steps
                playbackController.seekTo(position.coerceIn(Duration.ZERO, song.duration.coerceAtLeast(Duration.ZERO)))
                returnToTimeAfter(ScrubIdle)
            } else {
                val volume = playbackState.volume.first() + VOLUME_STEP * steps
                playbackController.setVolume(volume.coerceIn(MIN_VOLUME, MAX_VOLUME))
                mode.value = NowPlayingMode.Volume
                returnToTimeAfter(VolumeIdle)
            }
        }
    }

    override fun onCenter() {
        viewModelScope.launch {
            if (playbackState.currentSong.first() == null) return@launch
            if (mode.value == NowPlayingMode.Scrub) {
                returnToTime()
            } else {
                mode.value = NowPlayingMode.Scrub
                returnToTimeAfter(ScrubIdle)
            }
        }
    }

    override fun onBack() {
        if (mode.value == NowPlayingMode.Scrub) returnToTime()
    }

    private fun returnToTime() {
        idleJob?.cancel()
        mode.value = NowPlayingMode.Time
    }

    /** Restarts the wait: each turn gives the mode its whole time again. */
    private fun returnToTimeAfter(idle: Duration) {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(idle)
            mode.value = NowPlayingMode.Time
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
    }
}
