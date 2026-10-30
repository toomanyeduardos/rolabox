package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
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
    /** Where a turn left the marker, and the start of the item it was turned on. */
    private data class ScrubMarker(val start: Int, val position: Duration)

    private val mode = MutableStateFlow(NowPlayingMode.Time)

    /** `null` until the first turn of a scrub: the bar follows the song until then. */
    private val marker = MutableStateFlow<ScrubMarker?>(null)
    private var idleJob: Job? = null

    /** The start of the item scrub was selected on: ⏮ can start the same item again. */
    private var scrubStart: Int? = null

    override val uiState: StateFlow<NowPlayingUiState> = combine(
        combine(playbackState.currentItem, playbackState.startCount, ::Pair)
            .onEach { (item, start) -> endScrubOnAnotherStart(item, start) },
        playbackState.position,
        playbackState.queueIndex,
        playbackState.queue,
        combine(mode, playbackState.volume, marker, ::Triple),
    ) { (item, start), position, index, queue, (mode, volume, marker) ->
        if (item == null || index == null) {
            NowPlayingUiState.NothingLoaded
        } else {
            val shown = marker?.takeIf { mode == NowPlayingMode.Scrub && it.start == start }?.position ?: position
            NowPlayingUiState.Playing(
                title = item.title,
                artist = item.artist,
                album = item.album,
                number = index + 1,
                count = queue.size,
                position = shown.coerceIn(Duration.ZERO, item.duration.coerceAtLeast(Duration.ZERO)),
                duration = item.duration,
                mode = mode,
                volume = volume,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NowPlayingUiState.Loading)

    override fun onTurn(steps: Int) {
        viewModelScope.launch {
            val item = playbackState.currentItem.first() ?: return@launch
            if (mode.value == NowPlayingMode.Scrub) {
                val start = playbackState.startCount.first()
                val from = marker.value?.takeIf { it.start == start }?.position ?: playbackState.position.first()
                val end = item.duration.coerceAtLeast(Duration.ZERO)
                marker.value = ScrubMarker(start, (from + ScrubStep * steps).coerceIn(Duration.ZERO, end))
                acceptScrubAfterIdle()
            } else {
                // One step of the wheel is one step of the phone's volume, which stops at its own ends (ADR-023).
                playbackController.changeVolume(steps)
                mode.value = NowPlayingMode.Volume
                afterIdle(VolumeIdle) { mode.value = NowPlayingMode.Time }
            }
        }
    }

    override fun onCenter() {
        viewModelScope.launch {
            if (playbackState.currentItem.first() == null) return@launch
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

    /** Seeks to the marker, unless it was never moved or another item started under it, and leaves scrub. */
    private suspend fun acceptScrub() {
        val accepted = marker.value
        if (accepted != null && accepted.start == playbackState.startCount.first()) {
            playbackController.seekTo(accepted.position)
        }
        leaveScrub()
    }

    /**
     * Scrub doesn't carry over to another start of an item, whether the queue moved on, ⏮ started the same item
     * again or the queue ended (ADR-018, rule 16). It runs with [uiState], so only while the screen is observed
     * (ADR-006, rule 5): unobserved, scrub's own timer ends it, and a marker of another start is never sought.
     */
    private fun endScrubOnAnotherStart(item: PlaybackItem?, start: Int) {
        if (mode.value == NowPlayingMode.Scrub && (item == null || start != scrubStart)) {
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
    }
}
