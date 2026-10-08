package com.eduardoflores.rolabox.device.playback.impl

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A player with no engine and no clock. TEMPORARY: it is replaced when the playback ADR chooses an
 * engine, and only this class and its binding change, since everything it knows is a plain value.
 *
 * The position changes only when seeked, or when [next] and [previous] reset it to 0:00. Nothing here
 * blocks, so nothing switches dispatcher.
 */
@Singleton
internal class InMemoryPlayer @Inject constructor(private val library: LibraryRepository) :
    PlaybackState,
    PlaybackController {
    private val state = MutableStateFlow(Snapshot())

    override val currentSong: Flow<PlaybackSong?> = state.map { it.currentSong }.distinctUntilChanged()
    override val position: Flow<Duration> = state.map { it.position }.distinctUntilChanged()
    override val queue: Flow<List<PlaybackSong>> = state.map { it.queue }.distinctUntilChanged()
    override val queueIndex: Flow<Int?> = state.map { it.queueIndex }.distinctUntilChanged()
    override val volume: Flow<Int> = state.map { it.volume }.distinctUntilChanged()

    override suspend fun play(source: PlaybackSource, from: SongId): Either<PlaybackError, Unit> = either {
        val queue = loadQueue(source).bind()
        val index = queue.indexOfFirst { it.id == from }
        if (index < 0) raise(PlaybackError.SongNotInSource(from, source))
        state.update { it.copy(queue = queue, queueIndex = index, position = Duration.ZERO) }
    }

    override suspend fun seekTo(position: Duration) {
        state.update { snapshot ->
            val song = snapshot.currentSong ?: return@update snapshot
            snapshot.copy(position = position.coerceIn(Duration.ZERO, song.duration))
        }
    }

    override suspend fun next() = moveBy(1)

    override suspend fun previous() = moveBy(-1)

    override suspend fun setVolume(level: Int) {
        state.update { it.copy(volume = level.coerceIn(MIN_VOLUME, MAX_VOLUME)) }
    }

    private fun moveBy(step: Int) {
        state.update { snapshot ->
            val target = (snapshot.queueIndex ?: return@update snapshot) + step
            if (target !in snapshot.queue.indices) {
                snapshot
            } else {
                snapshot.copy(queueIndex = target, position = Duration.ZERO)
            }
        }
    }

    private suspend fun loadQueue(source: PlaybackSource): Either<PlaybackError, List<PlaybackSong>> = either {
        val songs = when (source) {
            PlaybackSource.AllSongs -> library.observeSongs()
            is PlaybackSource.ByArtist -> library.observeSongsByArtist(source.artistId)
            is PlaybackSource.ByAlbum -> library.observeSongsByAlbum(source.albumId)
        }.first().mapLeft(PlaybackError::Library).bind()
        val artists = mutableMapOf<ArtistId, String>()
        val albums = mutableMapOf<AlbumId, String>()
        songs.map { song ->
            if (song.artistId !in artists) {
                artists[song.artistId] = library.getArtist(song.artistId).mapLeft(PlaybackError::Library).bind().name
            }
            if (song.albumId !in albums) {
                albums[song.albumId] = library.getAlbum(song.albumId).mapLeft(PlaybackError::Library).bind().title
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
}

private data class Snapshot(
    val queue: List<PlaybackSong> = emptyList(),
    val queueIndex: Int? = null,
    val position: Duration = Duration.ZERO,
    val volume: Int = INITIAL_VOLUME,
) {
    val currentSong: PlaybackSong? get() = queueIndex?.let { queue[it] }
}

private const val MIN_VOLUME = 0
private const val MAX_VOLUME = 100
private const val INITIAL_VOLUME = 50
