package com.eduardoflores.rolabox.device.playback.impl

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import com.eduardoflores.rolabox.device.playback.api.SkippedItems
import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

/** How long [PlaybackState.skipped] keeps a skip, and so how long Now Playing says so. Tune it once it can be read. */
internal val SkipNotice: Duration = 4.seconds

/** How often the resume point is stored while an item plays, which is the most a kill can lose. */
internal val StoreEvery: Duration = 5.seconds

/**
 * [PlaybackState] and [PlaybackController] on Media3 (ADR-023). Every action is a command on the player that
 * [connection] gives, a `MediaController` of [PlaybackService]'s session, and the state is built from its events.
 * The volume is the exception: [phoneVolume] changes it, and the session reports it like any other change.
 *
 * It keeps what Media3 doesn't: the [PlaybackItem]s of the queue, the count of starts, the hold of ⏮ or ⏭, and the
 * items that couldn't be played. While an item plays or is being sought, the position is read every [Tick]. When the
 * queue ends, the items are cleared, so nothing is loaded. It runs in the application scope, since playback outlives
 * the screens (ADR-006, rule 6), and calls the player on its thread, where every field below is read and written.
 * Nothing blocks.
 *
 * It also stores where it is in [local], and restores that when the app was killed: loaded and paused from [restore],
 * and for a media button or the system's controls from [resumption], which plays it (ADR-023).
 */
// One function for each action of the controller, and the few that every action shares. One parameter for each of
// what it plays with: the library, the player, the volume, the stored point, the scope, the clock and the artwork.
@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
internal class MediaPlayback @Inject constructor(
    private val library: LibraryRepository,
    private val connection: PlayerConnection,
    private val phoneVolume: PhoneVolume,
    private val local: PlaybackLocalDataSource,
    @ApplicationScope private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
    private val artwork: ArtworkRepository,
) : PlaybackState,
    PlaybackController,
    SessionResumption {
    private val state = MutableStateFlow(Snapshot())

    /** The items the player was given, in its order. Empty when nothing is loaded. */
    private var items: List<PlaybackItem> = emptyList()

    /** Where the player was told each item's audio is. */
    private var uris: Map<PlaybackItemId, String> = emptyMap()

    /** How many times each item's artwork changed while it played, which its artwork URI carries. */
    private var artworkVersions: Map<PlaybackItemId, Int> = emptyMap()

    /** The list that was played, which is what is stored of the queue. */
    private var source: PlaybackSource? = null

    /** What to write to [local], the latest only, and `null` to clear it. Written in order, so a clear stays one. */
    private val toStore = Channel<ResumePoint?>(Channel.CONFLATED)
    private var hold: Hold? = null
    private var listenedTo: Player? = null

    /** The items of this queue that failed, which a skip goes past. */
    private var failed: Set<PlaybackItemId> = emptySet()

    /** Whether ⏮ brought the player to an item that hasn't played yet, so a skip goes on backward. */
    private var goingBack = false

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            listenedTo?.let { skip(it, error.toUnplayable()) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A new list counts its own start in play(), and a repeat isn't used.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            ) {
                state.update { it.copy(startCount = it.startCount + 1) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) goingBack = false
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
        // Again for each new queue, so that what is applied is never older than the queue it is applied to.
        scope.launch {
            state.map { snapshot -> snapshot.queue.map { it.id } }.distinctUntilChanged().collectLatest { queue ->
                if (queue.isNotEmpty()) {
                    library.observeSongLocations().collect { locations ->
                        locations.onRight { onPlayer { player -> follow(it, player) } }
                    }
                }
            }
        }
        // A cover that arrives for the item playing, downloaded for its album, reaches the notification and the lock
        // screen (ADR-027): its first value is the one the session loads anyway.
        scope.launch {
            state.map { it.currentItem }.distinctUntilChanged { old, new -> old?.id == new?.id }.collectLatest { item ->
                val ref = item?.artwork ?: return@collectLatest
                artwork.observeArtwork(ref).drop(1).collect { onPlayer { player -> showNewArtwork(item, player) } }
            }
        }
        // On each pause and each new item, and every so often while it plays.
        scope.launch {
            state.map { it.currentItem?.id to it.isPlaying }.distinctUntilChanged().collectLatest { (item, playing) ->
                if (item != null) {
                    onPlayer(::store)
                    while (playing) {
                        delay(StoreEvery)
                        onPlayer(::store)
                    }
                }
            }
        }
        // A point that can't be written loses nothing but where to carry on from, so nothing is told (ADR-007, rule 7).
        scope.launch {
            for (point in toStore) {
                if (point != null) local.store(point) else local.clear()
            }
        }
        scope.launch {
            state.map { it.skipped }.distinctUntilChanged().collectLatest { skipped ->
                if (skipped != null) {
                    delay(SkipNotice)
                    state.update { it.copy(skipped = null) }
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
    override val skipped: Flow<SkippedItems?> = state.map { it.skipped }.distinctUntilChanged()
    override val unplayableQueue: Flow<SkippedItems?> = state.map { it.unplayableQueue }.distinctUntilChanged()

    override suspend fun play(source: PlaybackSource, from: PlaybackItemId): Either<PlaybackError, Unit> = either {
        val queue = library.queueOf(source).bind()
        val index = queue.indexOfFirst { it.item.id == from }
        if (index < 0) raise(PlaybackError.ItemNotInSource(from, source))
        onPlayer { player ->
            load(source, queue)
            // The whole queue, so the next item is prepared before the current one ends (ADR-023).
            player.setMediaItems(queue.map { it.toMediaItem() }, index, 0)
            player.prepare()
            player.play()
            state.update { it.copy(startCount = it.startCount + 1, skipped = null, unplayableQueue = null) }
            refresh(player)
            // The same item in another list changes no state, and the list is what is stored.
            store(player)
        }.bind()
    }

    /**
     * Loads what was stored, paused where it was, when nothing is loaded. An item the user played in the meantime
     * stays, and so does anything the session already has. It is safe to call at any time.
     */
    suspend fun restore() {
        var empty = false
        onPlayer { empty = it.mediaItemCount == 0 }
        if (!empty) return
        val stored = storedQueue() ?: return
        onPlayer { player ->
            if (player.mediaItemCount == 0) {
                load(stored.source, stored.queue)
                player.setMediaItems(stored.queue.map { it.toMediaItem() }, stored.index, stored.position)
                player.pause()
                player.prepare()
                refresh(player)
            }
        }
    }

    // The session plays what this returns, and the state follows from its events.
    // MediaItemsWithStartPosition is unstable in Media3, and what its resumption asks for.
    @OptIn(UnstableApi::class)
    override fun resumption(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val resumed = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch(connection.dispatcher) {
            val stored = storedQueue()
            if (stored == null) {
                resumed.setException(UnsupportedOperationException("Nothing was stored to resume"))
            } else {
                load(stored.source, stored.queue)
                resumed.set(
                    MediaSession.MediaItemsWithStartPosition(
                        stored.queue.map { it.toMediaItem() },
                        stored.index,
                        stored.position,
                    ),
                )
            }
        }
        return resumed
    }

    override fun storeFrom(player: Player) = store(player)

    /** Stores where it is now, if anything is loaded. */
    suspend fun storeNow() {
        onPlayer(::store)
    }

    private fun load(source: PlaybackSource, queue: List<QueuedItem>) {
        hold = null
        this.source = source
        items = queue.map { it.item }
        uris = queue.associate { it.item.id to it.uri }
        artworkVersions = emptyMap()
        failed = emptySet()
        goingBack = false
    }

    /**
     * The stored list as it is now, and where in it to carry on. A song that is gone leaves the next one at its index,
     * from 0:00. Nothing is left when that is past the end, or the list is gone, so nothing is stored any more. A
     * library that can't be read says nothing about the songs, so what is stored stays.
     */
    private suspend fun storedQueue(): StoredQueue? {
        val point = local.resumePoint().getOrNull() ?: return null
        return library.queueOf(point.source).fold(
            ifLeft = { error ->
                if (error.isListGone()) local.clear()
                null
            },
            ifRight = { queue ->
                val stored = queue.resumedAt(point)
                if (stored == null) local.clear()
                stored
            },
        )
    }

    private fun store(player: Player) {
        val list = source ?: return
        val index = player.currentMediaItemIndex
        val item = items.getOrNull(index)
        if (item != null && player.mediaItemCount == items.size) {
            val position = player.currentPosition.coerceAtLeast(0).milliseconds
            toStore.trySend(ResumePoint(list, item.id, index, position))
        }
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
        goingBack = false
        // Does nothing on the last item.
        player.seekToNext()
    }

    override suspend fun previous() = onLoaded { player ->
        val index = player.currentMediaItemIndex
        player.seekToPrevious()
        // Starting the same item again is no transition, so it is counted here.
        if (player.currentMediaItemIndex == index) {
            state.update { it.copy(startCount = it.startCount + 1) }
        } else {
            goingBack = true
        }
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
        failed = emptySet()
        state.update { Snapshot(startCount = it.startCount, volume = it.volume) }
    }

    /**
     * The current item couldn't be played: says so, and moves past it as playing or as paused as it was. When nothing
     * is left to move to, the queue stops, and says that before nothing is loaded, so Now Playing isn't popped for it.
     */
    private fun skip(player: Player, error: PlaybackError.Unplayable) {
        val index = player.currentMediaItemIndex
        val item = items.getOrNull(index) ?: return
        failed = failed + item.id
        state.update {
            it.copy(skipped = SkippedItems(item, error, (it.skipped?.count ?: 0) + 1))
        }
        val next = nextPlayable(index)
        if (next == null) {
            state.update { it.copy(unplayableQueue = it.skipped) }
            unload(player)
        } else {
            endHold(player)
            // Past the first item backward, a skip goes forward from there.
            goingBack = goingBack && next < index
            player.seekTo(next, 0)
            player.prepare()
        }
        refresh(player)
    }

    /** The nearest item that hasn't failed: the way ⏮ was going, and otherwise forward. The queue doesn't wrap. */
    private fun nextPlayable(from: Int): Int? {
        val after = (from + 1 until items.size).firstOrNull { items[it].id !in failed }
        val before = (from - 1 downTo 0).firstOrNull { items[it].id !in failed }
        return if (goingBack) before ?: after else after
    }

    private fun endHold(player: Player) {
        hold?.let { if (it.wasPlaying) player.play() }
        hold = null
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

    /**
     * Brings the queue up to date with the library. An item that is gone leaves it at once, and when that is the
     * current one the next item starts, or nothing is loaded when there is none after it. An item that moved is given
     * its new URI, unless it is playing: its file is already open. Given a new URI, an item that failed can be tried
     * again.
     */
    private fun follow(locations: Map<SongId, String>, player: Player) {
        if (items.isEmpty()) return
        val current = player.currentMediaItemIndex
        val gone = items.indices.filter { locations[items[it].songId] == null }
        if (current in gone && items.indices.none { it > current && it !in gone }) {
            unload(player)
        } else {
            if (current in gone) {
                endHold(player)
                state.update { it.copy(startCount = it.startCount + 1) }
            }
            // From the last, so that the indices left still hold.
            gone.asReversed().forEach { index ->
                items = items.filterIndexed { itemIndex, _ -> itemIndex != index }
                player.removeMediaItem(index)
            }
            moveToNewLocations(locations, player)
        }
        refresh(player)
    }

    /**
     * Gives the item playing an artwork URI of a new version, so Media3 loads its artwork again, through the session's
     * bitmap loader (ADR-027). Only the metadata changes, so the player goes on without a pause.
     */
    private fun showNewArtwork(item: PlaybackItem, player: Player) {
        val index = player.currentMediaItemIndex
        val uri = uris[item.id]
        if (items.getOrNull(index)?.id != item.id || uri == null) return
        artworkVersions = artworkVersions + (item.id to (artworkVersions[item.id] ?: 0) + 1)
        player.replaceMediaItem(index, queuedItem(item, uri).toMediaItem())
    }

    private fun queuedItem(item: PlaybackItem, uri: String) = QueuedItem(item, uri, artworkVersions[item.id] ?: 0)

    private fun moveToNewLocations(locations: Map<SongId, String>, player: Player) {
        items.forEachIndexed { index, item ->
            val uri = locations[item.songId] ?: return@forEachIndexed
            if (uri != uris[item.id] && index != player.currentMediaItemIndex) {
                uris = uris + (item.id to uri)
                failed = failed - item.id
                player.replaceMediaItem(index, queuedItem(item, uri).toMediaItem())
            }
        }
    }

    /** The queue ended (ADR-018): nothing is loaded. */
    private fun unload(player: Player) {
        if (items.isEmpty()) return
        items = emptyList()
        hold = null
        failed = emptySet()
        goingBack = false
        toStore.trySend(null)
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

private val PlaybackItem.songId: SongId
    get() = when (val itemId = id) {
        is PlaybackItemId.Song -> itemId.id
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
    val skipped: SkippedItems? = null,
    val unplayableQueue: SkippedItems? = null,
) {
    val currentItem: PlaybackItem? get() = queueIndex?.let(queue::getOrNull)

    /** Whether the position changes with time, so it has to be read. */
    val isMoving: Boolean get() = currentItem != null && (isPlaying || isSeeking)
}

/** An item of the queue, and where its audio is, which the player needs and the `:api` doesn't show. */
private class QueuedItem(val item: PlaybackItem, val uri: String, val artworkVersion: Int = 0) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        // A controller in another process drops the URI, and the session puts it back from here.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setArtworkUri(item.artwork?.let { artworkUriOf(it, artworkVersion) })
                .build(),
        )
        .build()
}

/** A stored list, loaded again: its items and where to carry on in them. */
private class StoredQueue(val source: PlaybackSource, val queue: List<QueuedItem>, val index: Int, val position: Long)

private fun List<QueuedItem>.resumedAt(point: ResumePoint): StoredQueue? {
    val index = indexOfFirst { it.item.id == point.item }
    return when {
        index >= 0 -> StoredQueue(point.source, this, index, point.position.inWholeMilliseconds)
        point.index in indices -> StoredQueue(point.source, this, point.index, 0)
        else -> null
    }
}

private fun PlaybackError.isListGone(): Boolean = this is PlaybackError.Library &&
    (cause is LibraryError.ArtistNotFound || cause is LibraryError.AlbumNotFound)

/** The list of [source] as the queue shows it, with the names of each song's artist and album and its URI. */
private suspend fun LibraryRepository.queueOf(source: PlaybackSource): Either<PlaybackError, List<QueuedItem>> =
    either {
        val songs = when (source) {
            PlaybackSource.AllSongs -> observeSongs()
            is PlaybackSource.ByArtist -> observeSongsByArtist(source.artistId)
            is PlaybackSource.ByAlbum -> observeSongsByAlbum(source.albumId)
        }.first().mapLeft(PlaybackError::Library).bind()
        val artists = mutableMapOf<ArtistId, String?>()
        val albums = mutableMapOf<AlbumId, String?>()
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
                artwork = ArtworkRef(song.id),
            )
            QueuedItem(item, song.uri)
        }
    }

private const val MAX_VOLUME = 100
