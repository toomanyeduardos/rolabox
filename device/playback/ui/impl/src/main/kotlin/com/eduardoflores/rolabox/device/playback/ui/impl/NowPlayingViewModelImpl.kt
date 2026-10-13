package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackSong
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far one step of the wheel moves the marker in scrub. Tune it once it can be felt. */
internal val ScrubStep: Duration = 1.seconds

/** How much one step of the wheel changes the volume, out of 100. Tune it once it can be felt. */
internal const val VOLUME_STEP = 1

/** How long scrub waits for a turn before it is accepted on its own (ADR-018, rule 16). */
internal val ScrubIdle: Duration = 3.seconds

/** How long the volume stays on the bar after a turn, before the time returns (ADR-018, rule 16). */
internal val VolumeIdle: Duration = 2.seconds

/**
 * Observes the playback state and owns none of it (ADR-018, rule 10): the screen can be popped while the
 * music continues, and the next one starts from the state again. What it does own is the mode of the
 * screen and the marker of scrub, which is only sought when scrub is accepted (ADR-018, rule 16).
 */
@HiltViewModel
internal class NowPlayingViewModelImpl @Inject constructor(
    private val playbackState: PlaybackState,
    private val playbackController: PlaybackController,
) : NowPlayingViewModel() {
    /** Where a turn left the marker, and the start of the song it was turned on. */
    private data class ScrubMarker(val start: Int, val position: Duration)

    private val mode = MutableStateFlow(NowPlayingMode.Time)

    /** `null` until the first turn of a scrub: the bar follows the song until then. */
    private val marker = MutableStateFlow<ScrubMarker?>(null)
    private var idleJob: Job? = null

    /** The start of the song scrub was selected on: ⏮ can start the same song again. */
    private var scrubStart: Int? = null

    override val uiState: StateFlow<NowPlayingUiState> = combine(
        combine(playbackState.currentSong, playbackState.startCount, ::Pair)
            .onEach { (song, start) -> endScrubOnAnotherStart(song, start) },
        playbackState.position,
        playbackState.queueIndex,
        playbackState.queue,
        combine(mode, playbackState.volume, marker, ::Triple),
    ) { (song, start), position, index, queue, (mode, volume, marker) ->
        if (song == null || index == null) {
            NowPlayingUiState.NothingLoaded
        } else {
            val shown = marker?.takeIf { mode == NowPlayingMode.Scrub && it.start == start }?.position ?: position
            NowPlayingUiState.Playing(
                title = song.title,
                artist = song.artist,
                album = song.album,
                number = index + 1,
                count = queue.size,
                position = shown.coerceIn(Duration.ZERO, song.duration.coerceAtLeast(Duration.ZERO)),
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
                val start = playbackState.startCount.first()
                val from = marker.value?.takeIf { it.start == start }?.position ?: playbackState.position.first()
                val end = song.duration.coerceAtLeast(Duration.ZERO)
                marker.value = ScrubMarker(start, (from + ScrubStep * steps).coerceIn(Duration.ZERO, end))
                acceptScrubAfterIdle()
            } else {
                val volume = playbackState.volume.first() + VOLUME_STEP * steps
                playbackController.setVolume(volume.coerceIn(MIN_VOLUME, MAX_VOLUME))
                mode.value = NowPlayingMode.Volume
                afterIdle(VolumeIdle) { mode.value = NowPlayingMode.Time }
            }
        }
    }

    override fun onCenter() {
        viewModelScope.launch {
            if (playbackState.currentSong.first() == null) return@launch
            if (mode.value == NowPlayingMode.Scrub) {
                idleJob?.cancel()
                acceptScrub()
            } else {
                scrubStart = playbackState.startCount.first()
                marker.value = null
                mode.value = NowPlayingMode.Scrub
                acceptScrubAfterIdle()
            }
        }
    }

    override fun onMenu() = cancelScrub()

    override fun onPlaybackButton() = cancelScrub()

    override fun onBackground() = cancelScrub()

    /** Leaves scrub without seeking. The volume keeps its own wait. */
    private fun cancelScrub() {
        if (mode.value != NowPlayingMode.Scrub) return
        idleJob?.cancel()
        leaveScrub()
    }

    private fun acceptScrubAfterIdle() = afterIdle(ScrubIdle) { acceptScrub() }

    /** Seeks to the marker, unless it was never moved or another song started under it, and leaves scrub. */
    private suspend fun acceptScrub() {
        val accepted = marker.value
        if (accepted != null && accepted.start == playbackState.startCount.first()) {
            playbackController.seekTo(accepted.position)
        }
        leaveScrub()
    }

    /**
     * Scrub doesn't carry over to another start of a song, whether the queue moved on, ⏮ started the same song
     * again or the queue ended (ADR-018, rule 16). It runs with [uiState], so only while the screen is observed
     * (ADR-006, rule 5): unobserved, scrub's own timer ends it, and a marker of another start is never sought.
     */
    private fun endScrubOnAnotherStart(song: PlaybackSong?, start: Int) {
        if (mode.value == NowPlayingMode.Scrub && (song == null || start != scrubStart)) {
            idleJob?.cancel()
            leaveScrub()
        }
    }

    private fun leaveScrub() {
        marker.value = null
        mode.value = NowPlayingMode.Time
    }

    /** Restarts the wait: each turn gives the mode its whole time again. */
    private fun afterIdle(idle: Duration, onIdle: suspend () -> Unit) {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(idle)
            onIdle()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
    }
}
