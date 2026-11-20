package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItem
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import com.eduardoflores.rolabox.device.playback.api.SkippedItems
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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
    artworkRepository: ArtworkRepository,
    podcastArtworkRepository: PodcastArtworkRepository,
) : NowPlayingViewModel() {
    /** Where a turn left the marker, and the start of the item it was turned on. */
    private data class ScrubMarker(val start: Int, val position: Duration)

    private val mode = MutableStateFlow(NowPlayingMode.Time)

    /** `null` until the first turn of a scrub: the bar follows the song until then. */
    private val marker = MutableStateFlow<ScrubMarker?>(null)
    private var idleJob: Job? = null

    /** The start of the item scrub was selected on: ⏮ can start the same item again. */
    private var scrubStart: Int? = null

    private val artwork = playbackState.currentItem.artworkOf(artworkRepository, podcastArtworkRepository)

    private val episode = combine(
        artwork,
        playbackState.isShuffled,
        playbackState.repeatMode,
        playbackState.download,
        combine(playbackState.waitingFor, playbackState.download.failedJustNow(), ::Pair),
    ) { artwork, shuffled, repeat, download, (waiting, failed) ->
        Extras(artwork, shuffled, repeat, download, waiting, failed)
    }

    override val uiState: StateFlow<NowPlayingUiState> = combine(
        combine(playbackState.currentItem, playbackState.startCount, ::Pair)
            .onEach { (item, start) -> endScrubOnAnotherStart(item, start) },
        combine(playbackState.position, playbackState.queueIndex, playbackState.queue, ::Triple),
        combine(mode, playbackState.volume, marker, ::Triple),
        combine(playbackState.skipped, playbackState.unplayableQueue, ::Pair),
        episode,
    ) { (item, start), (position, index, queue), (mode, volume, marker), (skipped, unplayableQueue), extras ->
        if (item == null || index == null) {
            unplayableQueue?.let { NowPlayingUiState.NothingLeftToPlay(it.toUi()) } ?: NowPlayingUiState.NothingLoaded
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
                skipped = skipped?.toUi(),
                artwork = extras.artwork,
                shuffled = extras.shuffled,
                repeat = extras.repeat,
                isEpisode = item.id is PlaybackItemId.Episode,
                downloaded = extras.download.downloaded(),
                tag = episodeTag(extras.waiting, extras.download),
                downloadFailed = extras.downloadFailed,
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

/**
 * The artwork of the item playing, `null` while it loads and when it has none (ADR-026): a song's cover, or an
 * episode's artwork, from the podcasts (ADR-031, Artwork). Another item clears it first, so the last item's artwork
 * never stands for this one. Artwork that arrives while the item plays, downloaded for its album or its episode,
 * replaces the placeholder (ADR-027). One that can't be read has none: the screen has nothing to say about it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<PlaybackItem?>.artworkOf(
    songs: ArtworkRepository,
    episodes: PodcastArtworkRepository,
): Flow<ArtworkImage?> = map { it?.id to it?.artwork }
    .distinctUntilChanged()
    .transformLatest { (id, ref) ->
        emit(null)
        when (id) {
            is PlaybackItemId.Song -> if (ref != null) emitAll(songs.observeArtwork(ref).map { it.getOrNull() })
            is PlaybackItemId.Episode -> emitAll(episodes.observeArtwork(id.id).map { it.getOrNull() })
            null -> Unit
        }
    }
    .distinctUntilChanged()

/** What of the playback state only an episode has, with the artwork and the order, which every item has. */
private data class Extras(
    val artwork: ArtworkImage?,
    val shuffled: Boolean,
    val repeat: RepeatMode,
    val download: ItemDownload?,
    val waiting: PlaybackError.Waiting?,
    val downloadFailed: Boolean,
)

/** How long Now Playing says that the loaded episode's download failed, as long as it says a song was skipped. */
internal val DownloadFailedNotice: Duration = 4.seconds

/** `true` for [DownloadFailedNotice] after the loaded item's download fails, and `false` otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<ItemDownload?>.failedJustNow(): Flow<Boolean> = distinctUntilChanged().transformLatest { download ->
    emit(download == ItemDownload.Failed)
    if (download == ItemDownload.Failed) {
        delay(DownloadFailedNotice)
        emit(false)
    }
}.distinctUntilChanged()

/** The lighter part of the bar: all of a downloaded episode, what it has while it downloads, and none otherwise. */
private fun ItemDownload?.downloaded(): Float? = when (this) {
    ItemDownload.Done -> 1f
    is ItemDownload.Downloading -> fraction
    is ItemDownload.Waiting -> fraction
    ItemDownload.Failed, null -> null
}

/** What the corner says: what the episode waits for first, then that it downloads (ADR-031, frames 28 and 37). */
private fun episodeTag(waiting: PlaybackError.Waiting?, download: ItemDownload?): EpisodeTag? = when {
    waiting == PlaybackError.MobileDataOff -> EpisodeTag.WaitingForWifi
    waiting == PlaybackError.NoConnection -> EpisodeTag.WaitingForConnection
    download is ItemDownload.Downloading -> EpisodeTag.Downloading(download.fraction.percent())
    download is ItemDownload.Waiting -> EpisodeTag.Downloading(download.fraction.percent())
    else -> null
}

// Rounded down, so 100 % is only said of a download that is done, which says nothing.
private fun Float.percent(): Int = (this * PERCENT).toInt().coerceIn(0, PERCENT - 1)

private const val PERCENT = 100

private fun SkippedItems.toUi() = SkippedSongs(
    title = last.title,
    others = count - 1,
    reason = when (error) {
        PlaybackError.Unplayable.Missing -> SkipReason.Missing
        PlaybackError.Unplayable.Damaged -> SkipReason.Damaged
        PlaybackError.Unplayable.Unsupported -> SkipReason.Unsupported
        PlaybackError.Unplayable.Protected -> SkipReason.Protected
        PlaybackError.Unplayable.Unreadable -> SkipReason.Unreadable
        PlaybackError.Unplayable.Refused -> SkipReason.Refused
        PlaybackError.Unplayable.EngineFailed -> SkipReason.Unknown
    },
)
