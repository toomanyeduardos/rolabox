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
import arrow.core.raise.ensure
import com.eduardoflores.rolabox.common.userdata.api.Repeat
import com.eduardoflores.rolabox.common.userdata.api.Shuffle
import com.eduardoflores.rolabox.common.userdata.api.UserData
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import com.eduardoflores.rolabox.device.playback.api.SeekDirection
import com.eduardoflores.rolabox.device.playback.api.SkippedItems
import com.eduardoflores.rolabox.device.playback.local.api.PlayOrder
import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint
import com.eduardoflores.rolabox.device.playback.local.api.ShuffledBy
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
import kotlinx.coroutines.flow.filterNotNull
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

/** The events after which the current item can be ready with no audio: it became ready, or its tracks or it changed. */
// Each is checked once: a skip moves the player on, and the next item's own events decide for it.
private val SILENT_EVENTS = intArrayOf(
    Player.EVENT_PLAYBACK_STATE_CHANGED,
    Player.EVENT_TRACKS_CHANGED,
    Player.EVENT_MEDIA_ITEM_TRANSITION,
)

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
 *
 * Shuffle and Repeat are the user's settings, which it reads from [userData] and applies at once (ADR-028). The repeat
 * mode is the player's, and so is the shuffle order, which [playQueue] makes and the player is given: the playlist
 * stays in the list's order.
 */
// One function for each action of the controller, and the few that every action shares. One parameter for each of
// what it plays with: the library, the player, the volume, the stored point, the scope, the clock, the artwork, the
// settings and the random orders.
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
    private val userData: UserDataRepository,
    private val orders: PlayOrders,
) : PlaybackState,
    PlaybackController,
    SessionResumption {
    private val state = MutableStateFlow(Snapshot())

    /** The items the player was given, in its order, which is the list's. Empty when nothing is loaded. */
    private var items: List<PlaybackItem> = emptyList()

    /** The order [items] play in, and the rounds of Repeat All (ADR-028). */
    private val playQueue = PlayQueue(orders)

    /** [items] in the order they play, made again only when the items or their order change. */
    private var ordered: List<PlaybackItem> = emptyList()
    private var orderedFrom: Pair<List<PlaybackItem>, List<Int>>? = null

    /** The order the player was last given, so the same one isn't sent again. */
    private var playerOrder: List<Int>? = null

    /** The Shuffle and Repeat settings, `null` until they are read (ADR-028, rule 1). */
    private val settings = MutableStateFlow<Settings?>(null)

    /** Where the player was told each item's audio is. */
    private var uris: Map<PlaybackItemId, String> = emptyMap()

    /** How many times each item's artwork changed while it played, which its artwork URI carries. */
    private var artworkVersions: Map<PlaybackItemId, Int> = emptyMap()

    /** The list that was played, which is what is stored of the queue. */
    private var source: PlaybackSource? = null

    /**
     * What to write to [local], in order, so a clear stays one and an order is never lost. A point is only the latest:
     * one followed by another is never written.
     */
    private val toStore = Channel<Write>(Channel.UNLIMITED)
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
            // A new list counts its own start in play(). Repeat One starting the item again is a start too (ADR-028).
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                state.update { it.copy(startCount = it.startCount + 1) }
            }
            listenedTo?.let(::applyOrder)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // ⏮'s direction lasts until an item plays: one with no audio is ready too, and is skipped on backward
            // (ADR-023).
            if (playbackState == Player.STATE_READY && listenedTo?.hasNoAudio() != true) goingBack = false
            // An item with no audio can end without ever being ready, at once: it didn't play, it failed.
            if (playbackState == Player.STATE_ENDED) {
                listenedTo?.let { if (it.hasNoAudio()) skip(it, PlaybackError.Unplayable.Unsupported) else unload(it) }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (player === listenedTo && player.isReadyWithNoAudio(events)) {
                skip(player, PlaybackError.Unplayable.Unsupported)
            } else {
                refresh(player)
            }
        }
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
            for (first in toStore) {
                val writes = generateSequence(first) { toStore.tryReceive().getOrNull() }.toList()
                writes.forEachIndexed { at, write ->
                    val replaced = write is Write.Point && writes.drop(at + 1).any { it !is Write.Order }
                    if (!replaced) write.to(local)
                }
            }
        }
        // Each change applies at once to what is loaded, and a value that arrives by sync as one set here (ADR-028). A
        // setting that can't be read is its default.
        scope.launch {
            userData.observeUserData()
                .map { result -> result.getOrNull().toSettings() }
                .distinctUntilChanged()
                .collect { new ->
                    settings.value = new
                    onPlayer { player -> follow(new, player) }
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
    override val isShuffled: Flow<Boolean> = state.map { it.isShuffled }.distinctUntilChanged()
    override val repeatMode: Flow<RepeatMode> = state.map { it.repeatMode }.distinctUntilChanged()

    // The queue holds only the songs that can be played: a protected one selected plays from the nearest that can be
    // (ADR-023).
    override suspend fun play(source: PlaybackSource, from: PlaybackItemId): Either<PlaybackError, Unit> = either {
        val all = library.queueOf(source).bind()
        val at = all.indexOfFirst { it.item.id == from }
        if (at < 0) raise(PlaybackError.ItemNotInSource(from, source))
        val list = all.playable()
        val first = all.playableFrom(at)
        if (first == null) {
            loadNothingPlayable(all).bind()
        } else {
            start(source, list, currentSettings().shuffle.shuffledBy, list.indexOf(first)).bind()
        }
    }

    // Shuffle Songs: shuffled by songs whatever the setting says, from a random item, of those that can be played
    // (ADR-028, rule 5).
    override suspend fun shuffle(source: PlaybackSource): Either<PlaybackError, Unit> = either {
        val all = library.queueOf(source).bind()
        ensure(all.isNotEmpty()) { PlaybackError.NothingToPlay(source) }
        val list = all.playable()
        if (list.isEmpty()) {
            loadNothingPlayable(all).bind()
        } else {
            start(source, list, ShuffledBy.ShuffleSongs, orders.randomIndex(list.size)).bind()
        }
    }

    /**
     * A list none of whose songs can be played, since all are protected: nothing is loaded, and the state says why
     * (ADR-023, ADR-018 rule 8), with the last of them and their count.
     */
    private suspend fun loadNothingPlayable(all: List<QueuedItem>): Either<PlaybackError, Unit> = onPlayer { player ->
        unload(player)
        val stopped = SkippedItems(all.last().item, PlaybackError.Unplayable.Protected, all.size)
        state.update { it.copy(startCount = it.startCount + 1, skipped = null, unplayableQueue = stopped) }
        refresh(player)
    }

    /** Loads [list] in place of what was loaded, in an order shuffled [by] a value or in its own, and plays [index]. */
    private suspend fun start(
        source: PlaybackSource,
        list: List<QueuedItem>,
        by: ShuffledBy?,
        index: Int,
    ): Either<PlaybackError, Unit> = onPlayer { player ->
        load(source, list, by, index)
        // The whole queue, so the next item is prepared before the current one ends (ADR-023).
        player.setMediaItems(list.map { it.toMediaItem() }, index, 0)
        applyOrder(player)
        player.prepare()
        player.play()
        state.update { it.copy(startCount = it.startCount + 1, skipped = null, unplayableQueue = null) }
        refresh(player)
        // The same item in another list changes no state, and the list is what is stored.
        store(player)
        storeOrder()
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
        val current = currentSettings()
        onPlayer { player ->
            if (player.mediaItemCount == 0) {
                load(stored, current)
                player.setMediaItems(stored.queue.map { it.toMediaItem() }, stored.index, stored.position)
                follow(current, player)
                player.pause()
                player.prepare()
                refresh(player)
                storeOrder()
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
            val current = currentSettings()
            if (stored == null) {
                resumed.setException(UnsupportedOperationException("Nothing was stored to resume"))
            } else {
                load(stored, current)
                storeOrder()
                resumed.set(
                    MediaSession.MediaItemsWithStartPosition(
                        stored.queue.map { it.toMediaItem() },
                        stored.index,
                        stored.position,
                    ),
                )
                // The session plays the items once it has them, and the order follows them (PlayOrderKeeper).
                val current = currentSettings()
                onPlayer { player -> follow(current, player) }
            }
        }
        return resumed
    }

    override fun storeFrom(player: Player) = store(player)

    /** Stores where it is now, if anything is loaded. */
    suspend fun storeNow() {
        onPlayer(::store)
    }

    private fun load(source: PlaybackSource, list: List<QueuedItem>, by: ShuffledBy?, index: Int) {
        loadItems(source, list)
        playQueue.load(list.map { it.place }, by, index)
    }

    /**
     * Loads what was stored, in the stored order when there is one. The settings are read as they are now, so a queue
     * stored shuffled comes back in the list's order when Shuffle is off, as if it had been turned off while it played
     * (ADR-028, "What is restored").
     */
    private fun load(stored: StoredQueue, settings: Settings) {
        loadItems(stored.source, stored.queue)
        val places = stored.queue.map { it.place }
        val order = stored.order
        if (order !=
            null
        ) {
            playQueue.restore(places, order.by, order.round)
        } else {
            playQueue.load(places, null, stored.index)
        }
        val by = settings.shuffle.shuffledBy
        if (playQueue.shuffledBy != ShuffledBy.ShuffleSongs &&
            playQueue.shuffledBy != by
        ) {
            playQueue.reorder(by, stored.index)
        }
    }

    private fun loadItems(source: PlaybackSource, list: List<QueuedItem>) {
        hold = null
        this.source = source
        items = list.map { it.item }
        uris = list.associate { it.item.id to it.uri }
        artworkVersions = emptyMap()
        failed = emptySet()
        goingBack = false
        playerOrder = null
    }

    /** The settings once they are read: what is played is in the order they say (ADR-028, rule 1). */
    private suspend fun currentSettings(): Settings = settings.filterNotNull().first()

    /**
     * Applies the settings to the player and to what is loaded, at once and without interrupting the loaded item
     * (ADR-028, rule 4). A change of Shuffle gives a new order from the loaded item, or the list's order again, except
     * to a queue of Shuffle Songs. A queue already in the order the setting says is left as it is.
     */
    private fun follow(settings: Settings, player: Player) {
        if (player.repeatMode != settings.repeat.media3) player.repeatMode = settings.repeat.media3
        state.update { it.copy(repeatMode = settings.repeat) }
        val by = settings.shuffle.shuffledBy
        if (items.isNotEmpty() && playQueue.shuffledBy != ShuffledBy.ShuffleSongs && playQueue.shuffledBy != by) {
            playQueue.reorder(by, player.currentMediaItemIndex)
            storeOrder()
        }
        applyOrder(player)
        refresh(player)
    }

    /**
     * Gives the player the shuffle mode and the order for the item it is on (ADR-028, rule 6), and starts the next
     * round when the queue just wrapped to it.
     */
    private fun applyOrder(player: Player) {
        if (items.isEmpty() || player.mediaItemCount != items.size) return
        val current = player.currentMediaItemIndex
        if (playQueue.startedNextRound(current)) storeOrder()
        val order = playQueue.playerOrder(current, state.value.repeatMode)
        if (player.shuffleModeEnabled != (order != null)) player.shuffleModeEnabled = order != null
        if (order != null && order != playerOrder) player.setPlayOrder(order.map { items[it].id.mediaId })
        playerOrder = order
    }

    /**
     * The stored list as it is now, and where in it to carry on. A song that is gone leaves the next one at its index,
     * from 0:00. Nothing is left when that is past the end, or the list is gone, so nothing is stored any more. A
     * library that can't be read says nothing about the songs, so what is stored stays. A shuffled queue comes back in
     * its stored order, and an order that can't be used is dropped (ADR-028, "What is restored").
     */
    private suspend fun storedQueue(): StoredQueue? {
        val point = local.resumePoint().getOrNull() ?: return null
        val order = local.playOrder().getOrNull()
        return library.queueOf(point.source).fold(
            ifLeft = { error ->
                if (error.isListGone()) local.clear()
                null
            },
            ifRight = { all ->
                // A song found protected since it was stored is left out, as it is of any queue (ADR-023).
                val list = all.playable()
                val stored = list.resumedAt(point, order?.roundIn(list, point))
                if (stored == null) local.clear()
                stored
            },
        )
    }

    /**
     * The round of a stored [PlayOrder] in [list]: a song that is gone leaves it, as it leaves the queue, and a song
     * new to the list goes at its end, in a random order. `null` when it doesn't hold the stored song.
     */
    private fun PlayOrder.roundIn(list: List<QueuedItem>, point: ResumePoint): StoredOrder? {
        if (point.item !in items) return null
        val indexOf = list.indices.associateBy { list[it].item.id }
        val kept = items.mapNotNull { indexOf[it] }
        val added = list.indices - kept.toSet()
        return StoredOrder(shuffledBy, kept + orders.shuffled(added))
    }

    // The index is where the item is in the play order (ADR-028), which is the list's when it isn't shuffled.
    private fun store(player: Player) {
        val list = source ?: return
        val index = player.currentMediaItemIndex
        val item = items.getOrNull(index)
        if (item != null && player.mediaItemCount == items.size) {
            val position = player.currentPosition.coerceAtLeast(0).milliseconds
            toStore.trySend(Write.Point(ResumePoint(list, item.id, playQueue.positionOf(index), position)))
        }
    }

    /** Stores the order when it changes, apart from the point: a whole library's order is too large to write often. */
    private fun storeOrder() {
        val by = playQueue.shuffledBy
        val order = by?.let { shuffledBy -> PlayOrder(playQueue.order.map { items[it].id }, shuffledBy) }
        toStore.trySend(Write.Order(order))
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
                        playerOrder = null
                        settings.value?.let { player.repeatMode = it.repeat.media3 }
                        refresh(player)
                    }
                    action(player)
                }
        }

    private fun forgetPlayer() {
        listenedTo?.removeListener(listener)
        listenedTo = null
        items = emptyList()
        playQueue.clear()
        hold = null
        failed = emptySet()
        playerOrder = null
        state.update { Snapshot(startCount = it.startCount, volume = it.volume, repeatMode = it.repeatMode) }
    }

    /**
     * The current item couldn't be played: says so, moves past it as playing or as paused as it was, and it leaves the
     * queue (ADR-023). When nothing is left to move to, the queue stops, and says that before nothing is loaded, so Now
     * Playing isn't popped for it.
     */
    private fun skip(player: Player, error: PlaybackError.Unplayable) {
        val index = player.currentMediaItemIndex
        val item = items.getOrNull(index) ?: return
        failed = failed + item.id
        state.update {
            it.copy(skipped = SkippedItems(item, error, (it.skipped?.count ?: 0) + 1))
        }
        val round = playQueue.order
        val next = playQueue.nextPlayable(index, state.value.repeatMode, goingBack) { items[it].id in failed }
        if (next == null) {
            state.update { it.copy(unplayableQueue = it.skipped) }
            unload(player)
        } else {
            endHold(player)
            // Past the first item backward, a skip goes forward from there.
            goingBack = goingBack && playQueue.positionOf(next) < playQueue.positionOf(index)
            player.seekTo(next, 0)
            // A skip past the end of a shuffled round under Repeat All started the next round (ADR-028).
            val startedRound = playQueue.order !== round
            leaveQueue(index, player)
            if (startedRound || playQueue.shuffledBy != null) storeOrder()
            applyOrder(player)
            player.prepare()
        }
        refresh(player)
    }

    /**
     * The item at [index], which failed and isn't the current one any more, leaves the queue and the player's
     * playlist, so the count and ⏮ and ⏭ see only items that play (ADR-023). Its place in a shuffled order goes with
     * it, and the items after it keep theirs (ADR-028).
     */
    private fun leaveQueue(index: Int, player: Player) {
        items = items.filterIndexed { itemIndex, _ -> itemIndex != index }
        playQueue.remove(index)
        player.removeMediaItem(index)
        playerOrder = null
    }

    /**
     * Whether the current item has no audio the phone can play. Media3 drops a track it can't read, such as an iTunes
     * Store purchase's FairPlay one, and plays the item anyway, with no error: ready, the time moves with no end and
     * nothing is heard. That item is as unplayable as one that fails (ADR-023). Media3 doesn't say why, so it is
     * `Unsupported`. Asked only once the item is ready or ended, when its tracks are known.
     */
    private fun Player.isReadyWithNoAudio(events: Player.Events): Boolean =
        SILENT_EVENTS.any(events::contains) && playbackState == Player.STATE_READY && hasNoAudio()

    private fun Player.hasNoAudio(): Boolean =
        items.getOrNull(currentMediaItemIndex) != null && !currentTracks.isTypeSupported(C.TRACK_TYPE_AUDIO)

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
        if (current in gone && !playQueue.hasNext(current, state.value.repeatMode) { it in gone }) {
            unload(player)
        } else {
            if (current in gone) {
                endHold(player)
                state.update { it.copy(startCount = it.startCount + 1) }
            }
            // From the last, so that the indices left still hold. The player's order loses them as the queue's does.
            gone.asReversed().forEach { index ->
                items = items.filterIndexed { itemIndex, _ -> itemIndex != index }
                playQueue.remove(index)
                player.removeMediaItem(index)
            }
            if (gone.isNotEmpty()) {
                playerOrder = null
                applyOrder(player)
                if (playQueue.shuffledBy != null) storeOrder()
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
        player.replaceMediaItem(index, queuedItem(index, item, uri).toMediaItem())
    }

    private fun queuedItem(index: Int, item: PlaybackItem, uri: String) =
        QueuedItem(item, uri, playQueue.placeOf(index), artworkVersions[item.id] ?: 0)

    private fun moveToNewLocations(locations: Map<SongId, String>, player: Player) {
        items.forEachIndexed { index, item ->
            val uri = locations[item.songId] ?: return@forEachIndexed
            if (uri != uris[item.id] && index != player.currentMediaItemIndex) {
                uris = uris + (item.id to uri)
                failed = failed - item.id
                player.replaceMediaItem(index, queuedItem(index, item, uri).toMediaItem())
            }
        }
    }

    /** The queue ended (ADR-018): nothing is loaded. */
    private fun unload(player: Player) {
        if (items.isEmpty()) return
        items = emptyList()
        playQueue.clear()
        hold = null
        failed = emptySet()
        goingBack = false
        playerOrder = null
        toStore.trySend(Write.Clear)
        player.clearMediaItems()
    }

    private fun refresh(player: Player) {
        val loaded = items.isNotEmpty() && player.mediaItemCount == items.size
        val device = player.deviceInfo
        val range = device.maxVolume - device.minVolume
        state.update { snapshot ->
            snapshot.copy(
                queue = if (loaded) inPlayOrder() else emptyList(),
                queueIndex = if (loaded) {
                    playQueue.positionOf(player.currentMediaItemIndex).takeIf {
                        it >= 0
                    }
                } else {
                    null
                },
                isShuffled = loaded && playQueue.shuffledBy != null,
                position = if (loaded) player.currentPosition.coerceAtLeast(0).milliseconds else Duration.ZERO,
                isPlaying = loaded && (hold?.wasPlaying ?: player.playsOrIsAboutTo()),
                isSeeking = loaded && hold != null,
                volume = if (range > 0) (player.deviceVolume - device.minVolume) * MAX_VOLUME / range else 0,
            )
        }
    }

    /** The items in the order they play (ADR-028, rule 7), made again only when the items or the order change. */
    private fun inPlayOrder(): List<PlaybackItem> {
        val from = orderedFrom
        if (from == null || from.first !== items || from.second !== playQueue.order) {
            ordered = playQueue.order.map(items::get)
            orderedFrom = items to playQueue.order
        }
        return ordered
    }
}

/** The settings playback follows (ADR-028). */
private data class Settings(val shuffle: Shuffle, val repeat: RepeatMode)

private fun UserData?.toSettings() = Settings(
    shuffle = this?.shuffle ?: Shuffle.OFF,
    repeat = when (this?.repeat ?: Repeat.OFF) {
        Repeat.OFF -> RepeatMode.Off
        Repeat.ONE -> RepeatMode.One
        Repeat.ALL -> RepeatMode.All
    },
)

private val Shuffle.shuffledBy: ShuffledBy?
    get() = when (this) {
        Shuffle.OFF -> null
        Shuffle.SONGS -> ShuffledBy.Songs
        Shuffle.ALBUMS -> ShuffledBy.Albums
    }

private val RepeatMode.media3: Int
    get() = when (this) {
        RepeatMode.Off -> Player.REPEAT_MODE_OFF
        RepeatMode.One -> Player.REPEAT_MODE_ONE
        RepeatMode.All -> Player.REPEAT_MODE_ALL
    }

/** A write to the stored point, in the order they are made. */
private sealed interface Write {
    suspend fun to(local: PlaybackLocalDataSource)

    data class Point(val point: ResumePoint) : Write {
        override suspend fun to(local: PlaybackLocalDataSource) {
            local.store(point)
        }
    }

    data class Order(val order: PlayOrder?) : Write {
        override suspend fun to(local: PlaybackLocalDataSource) {
            local.storeOrder(order)
        }
    }

    data object Clear : Write {
        override suspend fun to(local: PlaybackLocalDataSource) {
            local.clear()
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
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
) {
    val currentItem: PlaybackItem? get() = queueIndex?.let(queue::getOrNull)

    /** Whether the position changes with time, so it has to be read. */
    val isMoving: Boolean get() = currentItem != null && (isPlaying || isSeeking)
}

/**
 * An item of the queue, where its audio is, which the player needs and the `:api` doesn't show, and where it is in its
 * album, which the Albums order needs (ADR-028). A [isProtected] song is in the library's list, and never in a queue
 * (ADR-023).
 */
private class QueuedItem(
    val item: PlaybackItem,
    val uri: String,
    val place: AlbumPlace,
    val artworkVersion: Int = 0,
    val isProtected: Boolean = false,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(item.id.mediaId)
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

/** A stored list, loaded again: its items, where to carry on in them, and the round it was in when it was shuffled. */
private class StoredQueue(
    val source: PlaybackSource,
    val queue: List<QueuedItem>,
    val index: Int,
    val position: Long,
    val order: StoredOrder?,
)

/** A stored play order, as indices into the list it is loaded with. */
private class StoredOrder(val by: ShuffledBy, val round: List<Int>)

/** Where to carry on: the stored item, and when it is gone the one at its place, in the [order] when there is one. */
private fun List<QueuedItem>.resumedAt(point: ResumePoint, order: StoredOrder?): StoredQueue? {
    val index = indexOfFirst { it.item.id == point.item }
    val inPlayOrder = order?.round ?: indices.toList()
    return when {
        index >= 0 -> StoredQueue(point.source, this, index, point.position.inWholeMilliseconds, order)
        point.index in inPlayOrder.indices -> StoredQueue(point.source, this, inPlayOrder[point.index], 0, order)
        else -> null
    }
}

private fun PlaybackError.isListGone(): Boolean = this is PlaybackError.Library &&
    (cause is LibraryError.ArtistNotFound || cause is LibraryError.AlbumNotFound || cause is LibraryError.GenreNotFound)

/**
 * The list of [source] as the library shows it, with the names of each song's artist and album and its URI. Its
 * protected songs are in it, and [playable] leaves them out.
 */
private suspend fun LibraryRepository.queueOf(source: PlaybackSource): Either<PlaybackError, List<QueuedItem>> =
    either {
        val songs = when (source) {
            PlaybackSource.AllSongs -> observeSongs()
            is PlaybackSource.ByArtist -> observeSongsByArtist(source.artistId)
            is PlaybackSource.ByAlbum -> observeSongsByAlbum(source.albumId)
            is PlaybackSource.ByGenreAndArtist -> observeSongsByGenreAndArtist(source.genreId, source.artistId)
            is PlaybackSource.ByGenreAndAlbum -> observeSongsByGenreAndAlbum(source.genreId, source.albumId)
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
            QueuedItem(item, song.uri, song.place, isProtected = song.isProtected)
        }
    }

private val Song.place get() = AlbumPlace(albumId, discNumber, trackNumber)

/** What of a list a queue holds: its songs that can be played (ADR-023). */
private fun List<QueuedItem>.playable(): List<QueuedItem> = filterNot { it.isProtected }

/**
 * Where a queue of [list]'s songs that can be played starts, for a selected [at]: that song, or the next one after it
 * that can be played, or the one before it when none comes after (ADR-023). `null` when none can be played.
 */
private fun List<QueuedItem>.playableFrom(at: Int): QueuedItem? =
    drop(at).firstOrNull { !it.isProtected } ?: take(at).lastOrNull { !it.isProtected }

private const val MAX_VOLUME = 100
