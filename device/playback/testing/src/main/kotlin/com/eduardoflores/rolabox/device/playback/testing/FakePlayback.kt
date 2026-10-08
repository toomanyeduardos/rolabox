package com.eduardoflores.rolabox.device.playback.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackSong
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Playback that tests drive: [load], [unload], [positionAt] and [volumeAt] set what the observed flows
 * emit. The actions change nothing. They are recorded, so a test can check what a ViewModel asked for.
 * [play] answers with [playResult].
 */
@Singleton
class FakePlayback @Inject constructor() :
    PlaybackState,
    PlaybackController {
    private val queueState = MutableStateFlow<List<PlaybackSong>>(emptyList())
    private val queueIndexState = MutableStateFlow<Int?>(null)
    private val positionState = MutableStateFlow(Duration.ZERO)
    private val volumeState = MutableStateFlow(INITIAL_VOLUME)

    /** What [play] answers with. */
    var playResult: Either<PlaybackError, Unit> = Unit.right()

    data class PlayCall(val source: PlaybackSource, val from: SongId)

    val playCalls = mutableListOf<PlayCall>()
    val seekCalls = mutableListOf<Duration>()
    val volumeCalls = mutableListOf<Int>()
    var nextCalls = 0
        private set
    var previousCalls = 0
        private set

    override val currentSong: Flow<PlaybackSong?> =
        combine(queueState, queueIndexState) { queue, index -> index?.let(queue::getOrNull) }
    override val position: Flow<Duration> = positionState
    override val queue: Flow<List<PlaybackSong>> = queueState
    override val queueIndex: Flow<Int?> = queueIndexState
    override val volume: Flow<Int> = volumeState

    /** Loads [queue] with the song at [index] current, at 0:00. */
    fun load(queue: List<PlaybackSong>, index: Int) {
        queueState.value = queue
        queueIndexState.value = index
        positionState.value = Duration.ZERO
    }

    /** Back to nothing loaded. */
    fun unload() {
        queueState.value = emptyList()
        queueIndexState.value = null
        positionState.value = Duration.ZERO
    }

    fun positionAt(position: Duration) {
        positionState.value = position
    }

    fun volumeAt(level: Int) {
        volumeState.value = level
    }

    override suspend fun play(source: PlaybackSource, from: SongId): Either<PlaybackError, Unit> {
        playCalls += PlayCall(source, from)
        return playResult
    }

    override suspend fun seekTo(position: Duration) {
        seekCalls += position
    }

    override suspend fun next() {
        nextCalls++
    }

    override suspend fun previous() {
        previousCalls++
    }

    override suspend fun setVolume(level: Int) {
        volumeCalls += level
    }
}

private const val INITIAL_VOLUME = 50
