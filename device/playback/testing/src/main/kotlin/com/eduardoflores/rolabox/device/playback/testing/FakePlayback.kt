package com.eduardoflores.rolabox.device.playback.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Playback that tests drive: [load], [restarted], [unload], [playing], [positionAt] and [volumeAt] set what the
 * observed flows emit. The actions change nothing. They are recorded, so a test can check what a ViewModel asked for.
 * [play] answers with [playResult].
 */
// One function for each action of the controller, and one for each part of the state a test sets.
@Suppress("TooManyFunctions")
@Singleton
class FakePlayback @Inject constructor() :
    PlaybackState,
    PlaybackController {
    private val queueState = MutableStateFlow<List<PlaybackItem>>(emptyList())
    private val queueIndexState = MutableStateFlow<Int?>(null)
    private val playingState = MutableStateFlow(false)
    private val positionState = MutableStateFlow(Duration.ZERO)
    private val startCountState = MutableStateFlow(0)
    private val volumeState = MutableStateFlow(INITIAL_VOLUME)

    /** What [play] answers with. */
    var playResult: Either<PlaybackError, Unit> = Unit.right()

    data class PlayCall(val source: PlaybackSource, val from: PlaybackItemId)

    val playCalls = mutableListOf<PlayCall>()
    val seekCalls = mutableListOf<Duration>()
    val startSeekingCalls = mutableListOf<SeekDirection>()

    /** The steps of each [changeVolume]. */
    val volumeStepCalls = mutableListOf<Int>()
    var nextCalls = 0
        private set
    var previousCalls = 0
        private set
    var pauseCalls = 0
        private set
    var resumeCalls = 0
        private set
    var stopSeekingCalls = 0
        private set

    override val currentItem: Flow<PlaybackItem?> =
        combine(queueState, queueIndexState) { queue, index -> index?.let(queue::getOrNull) }
    override val isPlaying: Flow<Boolean> = playingState
    override val position: Flow<Duration> = positionState
    override val queue: Flow<List<PlaybackItem>> = queueState
    override val queueIndex: Flow<Int?> = queueIndexState
    override val startCount: Flow<Int> = startCountState
    override val volume: Flow<Int> = volumeState

    /** Loads [queue] with the item at [index] current, playing at 0:00. */
    fun load(queue: List<PlaybackItem>, index: Int) {
        queueState.value = queue
        queueIndexState.value = index
        playingState.value = true
        positionState.value = Duration.ZERO
        startCountState.value++
    }

    /** The loaded item starts again at 0:00, as ⏮ does past the first seconds of an item. */
    fun restarted() {
        positionState.value = Duration.ZERO
        startCountState.value++
    }

    /** Back to nothing loaded. */
    fun unload() {
        queueState.value = emptyList()
        queueIndexState.value = null
        playingState.value = false
        positionState.value = Duration.ZERO
    }

    /** Whether the loaded item is playing, or paused. */
    fun playing(isPlaying: Boolean) {
        playingState.value = isPlaying
    }

    fun positionAt(position: Duration) {
        positionState.value = position
    }

    fun volumeAt(level: Int) {
        volumeState.value = level
    }

    override suspend fun play(source: PlaybackSource, from: PlaybackItemId): Either<PlaybackError, Unit> {
        playCalls += PlayCall(source, from)
        return playResult
    }

    override suspend fun pause() {
        pauseCalls++
    }

    override suspend fun resume() {
        resumeCalls++
    }

    override suspend fun seekTo(position: Duration) {
        seekCalls += position
    }

    override suspend fun startSeeking(direction: SeekDirection) {
        startSeekingCalls += direction
    }

    override suspend fun stopSeeking() {
        stopSeekingCalls++
    }

    override suspend fun next() {
        nextCalls++
    }

    override suspend fun previous() {
        previousCalls++
    }

    override suspend fun changeVolume(steps: Int) {
        volumeStepCalls += steps
    }
}

private const val INITIAL_VOLUME = 50
