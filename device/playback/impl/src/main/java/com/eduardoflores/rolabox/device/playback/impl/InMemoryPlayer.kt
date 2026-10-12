package com.eduardoflores.rolabox.device.playback.impl

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackSong
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How often the position is brought up to date while it moves, which is how often its observers see it move. */
internal val Tick: Duration = 250.milliseconds

/** How many times faster than playing a held ⏮ or ⏭ moves the position. Tune it once it can be felt. */
internal const val SEEK_SPEED = 10

/**
 * A player with no engine: a clock in place of the audio (ADR-018). TEMPORARY: it is replaced when the
 * playback ADR chooses an engine, and only this class and its binding change, since everything it knows is a
 * plain value.
 *
 * While the song plays its position advances with [timeSource]. At its end the next song starts, and after
 * the last one nothing is loaded. The state says when its position was true, so every action first brings it
 * up to date, and the clock in [scope] does the same every [Tick] while the position moves. It runs in the
 * application scope, since the song goes on when its screen is gone (ADR-006, rule 6). Nothing here blocks.
 */
@Singleton
internal class InMemoryPlayer @Inject constructor(
    private val library: LibraryRepository,
    @ApplicationScope scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
) : PlaybackState,
    PlaybackController {
    private val state = MutableStateFlow(Snapshot(positionAt = timeSource.markNow()))

    init {
        scope.launch {
            state.map { it.isMoving }.distinctUntilChanged().collectLatest { moving ->
                while (moving) {
                    delay(Tick)
                    change { it }
                }
            }
        }
    }

    override val currentSong: Flow<PlaybackSong?> = state.map { it.currentSong }.distinctUntilChanged()
    override val isPlaying: Flow<Boolean> = state.map { it.isPlaying }.distinctUntilChanged()
    override val position: Flow<Duration> = state.map { it.position }.distinctUntilChanged()
    override val queue: Flow<List<PlaybackSong>> = state.map { it.queue }.distinctUntilChanged()
    override val queueIndex: Flow<Int?> = state.map { it.queueIndex }.distinctUntilChanged()
    override val volume: Flow<Int> = state.map { it.volume }.distinctUntilChanged()

    override suspend fun play(source: PlaybackSource, from: SongId): Either<PlaybackError, Unit> = either {
        val queue = library.queueOf(source).bind()
        val index = queue.indexOfFirst { it.id == from }
        if (index < 0) raise(PlaybackError.SongNotInSource(from, source))
        change {
            it.copy(queue = queue, queueIndex = index, position = Duration.ZERO, isPlaying = true, seeking = null)
        }
    }

    override suspend fun pause() = change { it.copy(isPlaying = false) }

    override suspend fun resume() = change { it.copy(isPlaying = it.currentSong != null) }

    override suspend fun seekTo(position: Duration) = change { snapshot ->
        val song = snapshot.currentSong ?: return@change snapshot
        snapshot.copy(position = position.coerceIn(Duration.ZERO, song.duration))
    }

    override suspend fun startSeeking(direction: SeekDirection) = change { snapshot ->
        if (snapshot.currentSong == null) snapshot else snapshot.copy(seeking = direction)
    }

    override suspend fun stopSeeking() = change { it.copy(seeking = null) }

    override suspend fun next() = moveBy(1)

    override suspend fun previous() = moveBy(-1)

    override suspend fun setVolume(level: Int) = change { it.copy(volume = level.coerceIn(MIN_VOLUME, MAX_VOLUME)) }

    private fun moveBy(step: Int) = change { snapshot ->
        val target = (snapshot.queueIndex ?: return@change snapshot) + step
        if (target !in snapshot.queue.indices) {
            snapshot
        } else {
            snapshot.copy(queueIndex = target, position = Duration.ZERO)
        }
    }

    /** Brings the state up to now, and then applies [transform] to it. */
    private fun change(transform: (Snapshot) -> Snapshot) {
        val now = timeSource.markNow()
        state.update { transform(it.at(now)) }
    }
}

/** The list of [source] as the queue shows it, with the names of each song's artist and album. */
private suspend fun LibraryRepository.queueOf(source: PlaybackSource): Either<PlaybackError, List<PlaybackSong>> =
    either {
        val songs = when (source) {
            PlaybackSource.AllSongs -> observeSongs()
            is PlaybackSource.ByArtist -> observeSongsByArtist(source.artistId)
            is PlaybackSource.ByAlbum -> observeSongsByAlbum(source.albumId)
        }.first().mapLeft(PlaybackError::Library).bind()
        val artists = mutableMapOf<ArtistId, String>()
        val albums = mutableMapOf<AlbumId, String>()
        songs.map { song ->
            if (song.artistId !in artists) {
                artists[song.artistId] = getArtist(song.artistId).mapLeft(PlaybackError::Library).bind().name
            }
            if (song.albumId !in albums) {
                albums[song.albumId] = getAlbum(song.albumId).mapLeft(PlaybackError::Library).bind().title
            }
            PlaybackSong(
                id = song.id,
                title = song.title,
                artist = artists.getValue(song.artistId),
                album = albums.getValue(song.albumId),
                duration = song.duration,
            )
        }
    }

/** The state at one moment: [position] is where the song was at [positionAt]. */
private data class Snapshot(
    val positionAt: ComparableTimeMark,
    val queue: List<PlaybackSong> = emptyList(),
    val queueIndex: Int? = null,
    val position: Duration = Duration.ZERO,
    val isPlaying: Boolean = false,
    val seeking: SeekDirection? = null,
    val volume: Int = INITIAL_VOLUME,
) {
    val currentSong: PlaybackSong? get() = queueIndex?.let { queue[it] }

    /** Whether the position changes with time, so the clock has to run. */
    val isMoving: Boolean get() = currentSong != null && (isPlaying || seeking != null)

    /**
     * The state at [now]. A song that is being sought moves at [SEEK_SPEED] and stops at its ends, without
     * ending. A song that plays advances, and ends when it reaches its duration.
     */
    fun at(now: ComparableTimeMark): Snapshot {
        val song = currentSong ?: return copy(positionAt = now)
        val elapsed = now - positionAt
        return when {
            seeking != null -> {
                val moved = elapsed * SEEK_SPEED * if (seeking == SeekDirection.Forward) 1 else -1
                copy(position = (position + moved).coerceIn(Duration.ZERO, song.duration), positionAt = now)
            }

            !isPlaying -> copy(positionAt = now)

            position + elapsed < song.duration -> copy(position = position + elapsed, positionAt = now)

            else -> afterTheSongEnds(now)
        }
    }

    /** The next song from 0:00, as the one that ended was. After the last one nothing is loaded. */
    private fun afterTheSongEnds(now: ComparableTimeMark): Snapshot {
        val next = (queueIndex ?: return this) + 1
        return if (next in queue.indices) {
            copy(queueIndex = next, position = Duration.ZERO, positionAt = now)
        } else {
            Snapshot(positionAt = now, volume = volume)
        }
    }
}

private const val MIN_VOLUME = 0
private const val MAX_VOLUME = 100
private const val INITIAL_VOLUME = 50
