package com.eduardoflores.rolabox.device.playback.impl

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
import kotlinx.coroutines.withContext

/**
 * How often the position is read from the player while it moves, which is how often its observers see it move.
 * Media3 doesn't push its position (ADR-023).
 */
internal val Tick: Duration = 250.milliseconds

/** How many times faster than playing a held ⏮ or ⏭ moves the position. Tune it once it can be felt. */
internal const val SEEK_SPEED = 10

/**
 * How far into an item ⏮ stops going to the previous one and starts the loaded one again (ADR-018, rule 17). At
 * this position or before it, ⏮ goes to the previous item. It is the player's `maxSeekToPreviousPosition`, so
 * the media buttons share it (ADR-023, rule 7).
 */
internal val RestartThreshold: Duration = 3.seconds

/**
 * [PlaybackState] and [PlaybackController] on Media3 (ADR-023). Every action is a command on the player that
 * [connection] gives, a `MediaController` of [PlaybackService]'s session, and the state is built from its events.
 * The volume is the exception: [phoneVolume] changes it, and the session reports it like any other change.
 *
 * It keeps what Media3 doesn't: the [PlaybackItem]s of the queue, the count of starts and the hold of ⏮ or ⏭.
 * While an item plays or is being sought, the position is read every [Tick]. When the queue ends, the items are
 * cleared, so nothing is loaded. It runs in the application scope, since playback outlives the screens (ADR-006,
 * rule 6), and calls the player on its thread, where every field below is read and written. Nothing blocks.
 */
// One function for each action of the controller, and the few that every action shares.
@Suppress("TooManyFunctions")
@Singleton
internal class MediaPlayback @Inject constructor(
    private val library: LibraryRepository,
    private val connection: PlayerConnection,
    private val phoneVolume: PhoneVolume,
    @ApplicationScope scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
) : PlaybackState,
    PlaybackController {
    private val state = MutableStateFlow(Snapshot())

    /** The items the player was given, in its order. Empty when nothing is loaded. */
    private var items: List<PlaybackItem> = emptyList()
    private var hold: Hold? = null
    private var listenedTo: Player? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A new list counts its own start in play(), and a repeat isn't used.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            ) {
                state.update { it.copy(startCount = it.startCount + 1) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) listenedTo?.let(::unload)
        }

        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
    }

    init {
        scope.launch {
            onPlayer { }
            state.map { it.isMoving }.distinctUntilChanged().collectLatest { moving ->
                while (moving) {
                    delay(Tick)
                    onPlayer { player ->
                        hold?.let { player.moveBy(it) }
                        refresh(player)
                    }
                }
            }
        }
    }

    override val currentItem: Flow<PlaybackItem?> = state.map { it.currentItem }.distinctUntilChanged()
    override val isPlaying: Flow<Boolean> = state.map { it.isPlaying }.distinctUntilChanged()
    override val position: Flow<Duration> = state.map { it.position }.distinctUntilChanged()
    override val queue: Flow<List<PlaybackItem>> = state.map { it.queue }.distinctUntilChanged()
    override val queueIndex: Flow<Int?> = state.map { it.queueIndex }.distinctUntilChanged()
    override val startCount: Flow<Int> = state.map { it.startCount }.distinctUntilChanged()
    override val volume: Flow<Int> = state.map { it.volume }.distinctUntilChanged()

    override suspend fun play(source: PlaybackSource, from: PlaybackItemId): Either<PlaybackError, Unit> = either {
        val queue = library.queueOf(source).bind()
        val index = queue.indexOfFirst { it.item.id == from }
        if (index < 0) raise(PlaybackError.ItemNotInSource(from, source))
        onPlayer { player ->
            hold = null
            items = queue.map { it.item }
            // The whole queue, so the next item is prepared before the current one ends (ADR-023).
            player.setMediaItems(queue.map { it.toMediaItem() }, index, 0)
            player.prepare()
            player.play()
            state.update { it.copy(startCount = it.startCount + 1) }
            refresh(player)
        }.bind()
    }

    override suspend fun pause() = onLoaded { player ->
        hold?.let { it.wasPlaying = false } ?: player.pause()
    }

    override suspend fun resume() = onLoaded { player ->
        hold?.let { it.wasPlaying = true } ?: player.play()
    }

    override suspend fun seekTo(position: Duration) = onLoaded { player ->
        player.seekTo(position.coerceIn(Duration.ZERO, player.end()).inWholeMilliseconds)
    }

    override suspend fun startSeeking(direction: SeekDirection) = onLoaded { player ->
        val held = hold
        if (held != null) {
            player.moveBy(held)
            held.direction = direction
        } else {
            // Paused under the hold, so it can stop at the end without ending the item. It is still playing to
            // everyone who observes the state, and plays on from where the hold leaves it.
            hold = Hold(direction, player.playWhenReady, timeSource.markNow())
            player.pause()
        }
    }

    override suspend fun stopSeeking() = onLoaded { player ->
        val held = hold ?: return@onLoaded
        player.moveBy(held)
        hold = null
        if (held.wasPlaying) player.play()
    }

    override suspend fun next() = onLoaded { player ->
        // Does nothing on the last item.
        player.seekToNext()
    }

    override suspend fun previous() = onLoaded { player ->
        val index = player.currentMediaItemIndex
        player.seekToPrevious()
        // Starting the same item again is no transition, so it is counted here.
        if (player.currentMediaItemIndex == index) state.update { it.copy(startCount = it.startCount + 1) }
    }

    // The state follows when the session reports the new volume, as it does for the phone's keys.
    override suspend fun changeVolume(steps: Int) {
        onPlayer { phoneVolume.change(steps) }
    }

    /** Runs [action] on the player when something is loaded, and then brings the state up to date. */
    private suspend fun onLoaded(action: (Player) -> Unit) {
        onPlayer { player ->
            if (items.isNotEmpty()) {
                action(player)
                refresh(player)
            }
        }
    }

    /**
     * Runs [action] on the player. When it can't be reached nothing is loaded, so that is what the state says, and
     * only [play] has to tell: every other action does nothing when nothing is loaded.
     */
    private suspend fun onPlayer(action: (Player) -> Unit): Either<PlaybackError, Unit> =
        withContext(connection.dispatcher) {
            connection.player()
                .onLeft { forgetPlayer() }
                .map { player ->
                    if (player !== listenedTo) {
                        listenedTo?.removeListener(listener)
                        player.addListener(listener)
                        listenedTo = player
                        refresh(player)
                    }
                    action(player)
                }
        }

    private fun forgetPlayer() {
        listenedTo?.removeListener(listener)
        listenedTo = null
        items = emptyList()
        hold = null
        state.update { Snapshot(startCount = it.startCount, volume = it.volume) }
    }

    /** Moves a held item by the time since [held] last moved it, between 0:00 and its end. */
    private fun Player.moveBy(held: Hold) {
        val now = timeSource.markNow()
        val moved = (now - held.movedAt) * SEEK_SPEED * if (held.direction == SeekDirection.Forward) 1 else -1
        held.movedAt = now
        seekTo((currentPosition.milliseconds + moved).coerceIn(Duration.ZERO, end()).inWholeMilliseconds)
    }

    /** Where the current item ends: the player's duration once it knows it, and the library's until then. */
    private fun Player.end(): Duration {
        val known = duration.takeIf { it != C.TIME_UNSET }?.milliseconds
        return known ?: items.getOrNull(currentMediaItemIndex)?.duration ?: Duration.ZERO
    }

    /**
     * Whether it plays, buffering included. Losing the focus for a while leaves `playWhenReady` on, so that it
     * resumes when the focus returns, but it is paused until then (ADR-023).
     */
    private fun Player.playsOrIsAboutTo(): Boolean =
        playWhenReady && playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS

    /** The queue ended (ADR-018): nothing is loaded. */
    private fun unload(player: Player) {
        if (items.isEmpty()) return
        items = emptyList()
        hold = null
        player.clearMediaItems()
    }

    private fun refresh(player: Player) {
        val loaded = items.isNotEmpty() && player.mediaItemCount == items.size
        val device = player.deviceInfo
        val range = device.maxVolume - device.minVolume
        state.update { snapshot ->
            snapshot.copy(
                queue = if (loaded) items else emptyList(),
                queueIndex = if (loaded) player.currentMediaItemIndex else null,
                position = if (loaded) player.currentPosition.coerceAtLeast(0).milliseconds else Duration.ZERO,
                isPlaying = loaded && (hold?.wasPlaying ?: player.playsOrIsAboutTo()),
                isSeeking = loaded && hold != null,
                volume = if (range > 0) (player.deviceVolume - device.minVolume) * MAX_VOLUME / range else 0,
            )
        }
    }
}

/** A hold of ⏮ or ⏭: which way it moves, whether the item plays once it ends, and when it last moved. */
private class Hold(var direction: SeekDirection, var wasPlaying: Boolean, var movedAt: ComparableTimeMark)

/** What the flows of [PlaybackState] read. */
private data class Snapshot(
    val queue: List<PlaybackItem> = emptyList(),
    val queueIndex: Int? = null,
    val position: Duration = Duration.ZERO,
    val startCount: Int = 0,
    val isPlaying: Boolean = false,
    val isSeeking: Boolean = false,
    val volume: Int = 0,
) {
    val currentItem: PlaybackItem? get() = queueIndex?.let(queue::getOrNull)

    /** Whether the position changes with time, so it has to be read. */
    val isMoving: Boolean get() = currentItem != null && (isPlaying || isSeeking)
}

/** An item of the queue, and where its audio is, which the player needs and the `:api` doesn't show. */
private class QueuedItem(val item: PlaybackItem, val uri: String) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        // A controller in another process drops the URI, and the session puts it back from here.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .build(),
        )
        .build()
}

/** The list of [source] as the queue shows it, with the names of each song's artist and album and its URI. */
private suspend fun LibraryRepository.queueOf(source: PlaybackSource): Either<PlaybackError, List<QueuedItem>> =
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
            val item = PlaybackItem(
                id = PlaybackItemId.Song(song.id),
                title = song.title,
                artist = artists.getValue(song.artistId),
                album = albums.getValue(song.albumId),
                duration = song.duration,
            )
            QueuedItem(item, song.uri)
        }
    }

private const val MAX_VOLUME = 100
