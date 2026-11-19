package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.playback.api.EpisodeNetwork
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.Show
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row of the Episode screen: what center on it does. */
internal enum class EpisodeRow {
    /** From 0:00: an unplayed episode, a played one, or one of a show opened from search. */
    Play,

    /** From where the user stopped, for one partly played. */
    Resume,

    /** From 0:00, for one partly played. */
    PlayFromStart,

    /**
     * In place of Play, for an episode with no download while episodes need Wi-Fi (ADR-031, frame 36). Center opens
     * the podcasts' settings, where Use Mobile Data is.
     */
    NeedsWifi,
}

/** Why center couldn't play the episode, as the screen tells the user (ADR-018, rule 18). */
internal enum class EpisodePlayFailure {
    /** The episode, its show or its position couldn't be read, or it is no longer in the library. */
    EpisodeUnavailable,

    /** The player couldn't be reached. */
    PlayerUnavailable,

    /** The phone hasn't room for the episode's download (ADR-031, Downloads). */
    StorageFull,

    /** The episode has no download, and Use Mobile Data is Off on a metered network (ADR-031, Mobile data). */
    MobileDataOff,
}

/**
 * What the info line under the rows says: the date the episode was published, its length, and the time [left] of
 * one partly played. Each is `null` when it isn't known or doesn't apply, and is left out.
 */
internal data class EpisodeInfo(val publishedAt: Instant?, val length: Duration?, val left: Duration?)

/** The Episode screen (frame 26): the show's title in the header, the rows, the info line and the description. */
internal data class EpisodeContent(
    val showTitle: String,
    val rows: List<EpisodeRow>,
    val info: EpisodeInfo,
    val description: String?,
)

/**
 * Where the wheel is on the Episode screen: on the row at [row], and [lines] lines into the description. The text
 * is only scrolled once the last row is highlighted, so turning back scrolls it up before it moves the highlight.
 */
internal data class EpisodeWheel(val row: Int = 0, val lines: Int = 0) {
    /**
     * The wheel turned by [steps], with [rowCount] rows and a description that can be scrolled [scrollableLines]
     * lines. Turning forward moves the highlight to the last row, then scrolls the text a line a step, and stops at
     * its end. Turning back scrolls the text up to the top, then moves the highlight, and stops at the first row.
     */
    fun turned(steps: Int, rowCount: Int, scrollableLines: Int): EpisodeWheel {
        if (rowCount == 0) return this
        val lastRow = rowCount - 1
        var row = row.coerceIn(0, lastRow)
        var lines = if (row == lastRow) lines.coerceIn(0, scrollableLines.coerceAtLeast(0)) else 0
        if (steps > 0) {
            val toRows = minOf(steps, lastRow - row)
            row += toRows
            lines = (lines + steps - toRows).coerceAtMost(scrollableLines.coerceAtLeast(0))
        } else if (steps < 0) {
            val fromText = minOf(-steps, lines)
            lines -= fromText
            row = (row - (-steps - fromText)).coerceAtLeast(0)
        }
        return EpisodeWheel(row, lines)
    }
}

/**
 * The Episode screen of an episode in the library (frame 26), and where the wheel is on it (ADR-021).
 * [EpisodeViewModelImpl] implements it.
 */
internal abstract class EpisodeViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<EpisodeContent>>

    /** The highlighted row and how far the description is scrolled, saved with the entry (ADR-018, rule 7). */
    abstract val wheel: StateFlow<EpisodeWheel>

    abstract fun onTurn(steps: Int)

    /** How many lines the description can be scrolled, as the screen measured it at this width and font scale. */
    abstract fun onScrollableLinesChange(lines: Int)

    /** Why the last center couldn't play, until [onPlayFailureShown]. `null` when there is nothing to show. */
    abstract val playFailure: StateFlow<EpisodePlayFailure?>

    /**
     * Center: plays the episode as the highlighted row says, alone (ADR-031, rule 11), and then calls [onPlaying]. Play
     * and Play from Start start at 0:00, and Resume where the user stopped. When it can't play, [onPlaying] isn't
     * called, and [playFailure] says why. On Needs Wi-Fi it plays nothing, and calls [onAllowMobileData].
     */
    abstract fun onCenter(onPlaying: () -> Unit, onAllowMobileData: () -> Unit)

    /** The message of [playFailure] was shown, so it isn't shown again. */
    abstract fun onPlayFailureShown()
}

/** The episode is the one of the entry's key, given when the entry creates the ViewModel (see [ShowViewModelImpl]). */
@HiltViewModel(assistedFactory = EpisodeViewModelImpl.Factory::class)
internal class EpisodeViewModelImpl @AssistedInject constructor(
    @Assisted episodeKey: Long,
    savedState: SavedStateHandle,
    podcastsRepository: PodcastsRepository,
    private val playbackController: PlaybackController,
    episodeNetwork: EpisodeNetwork,
) : EpisodeViewModel() {
    private val episodeId = EpisodeId(episodeKey)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val uiState: StateFlow<ListUiState<EpisodeContent>> =
        podcastsRepository.observeEpisode(episodeId)
            .flatMapLatest { episode ->
                episode.fold(
                    ifLeft = { flowOf(Either.Left(it)) },
                    ifRight = { found ->
                        withShow(podcastsRepository.observeShow(found.showId), found, episodeNetwork.observeNeedsWifi())
                    },
                )
            }
            .asListState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    private val savedWheel = SavedEpisodeWheel(savedState, viewModelScope)
    override val wheel: StateFlow<EpisodeWheel> = savedWheel.wheel

    private val playFailureState = MutableStateFlow<EpisodePlayFailure?>(null)
    override val playFailure: StateFlow<EpisodePlayFailure?> = playFailureState.asStateFlow()

    override fun onTurn(steps: Int) {
        val rowCount = (uiState.value as? ListUiState.Loaded)?.value?.rows?.size ?: return
        savedWheel.turn(steps, rowCount)
    }

    override fun onScrollableLinesChange(lines: Int) = savedWheel.setScrollableLines(lines)

    // Playing downloads the episode, and plays it as it arrives (ADR-031, rule 8).
    override fun onCenter(onPlaying: () -> Unit, onAllowMobileData: () -> Unit) {
        val rows = (uiState.value as? ListUiState.Loaded)?.value?.rows ?: return
        val row = rows.getOrNull(savedWheel.current().row.coerceIn(0, rows.lastIndex)) ?: return
        if (row == EpisodeRow.NeedsWifi) onAllowMobileData() else play(row, onPlaying)
    }

    private fun play(row: EpisodeRow, onPlaying: () -> Unit) {
        viewModelScope.launch {
            playbackController.play(
                source = PlaybackSource.Episode(episodeId),
                from = PlaybackItemId.Episode(episodeId),
                resume = row == EpisodeRow.Resume,
            ).fold(
                ifLeft = { error -> playFailureState.value = error.toEpisodePlayFailure() },
                ifRight = { onPlaying() },
            )
        }
    }

    override fun onPlayFailureShown() {
        playFailureState.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(episodeKey: Long): EpisodeViewModelImpl
    }
}

/**
 * The Episode screen's wheel, saved with its entry (ADR-018, rule 7): the highlighted row and how far the description
 * is scrolled, for the episodes of the library and those from search alike.
 */
internal class SavedEpisodeWheel(private val savedState: SavedStateHandle, scope: CoroutineScope) {
    val wheel: StateFlow<EpisodeWheel> = combine(
        savedState.getStateFlow(ROW_KEY, 0),
        savedState.getStateFlow(LINES_KEY, 0),
    ) { row, lines -> EpisodeWheel(row, lines) }
        .stateIn(scope, SharingStarted.Eagerly, current())

    private var scrollableLines = 0

    fun current() = EpisodeWheel(savedState[ROW_KEY] ?: 0, savedState[LINES_KEY] ?: 0)

    /** The wheel turned by [steps], over [rowCount] rows and then the description. */
    fun turn(steps: Int, rowCount: Int) = save(current().turned(steps, rowCount, scrollableLines))

    fun setScrollableLines(lines: Int) {
        scrollableLines = lines.coerceAtLeast(0)
        // A larger font or a shorter text leaves fewer lines to scroll: the text stays at its end.
        val wheel = current()
        if (wheel.lines > scrollableLines) save(wheel.copy(lines = scrollableLines))
    }

    private fun save(wheel: EpisodeWheel) {
        savedState[ROW_KEY] = wheel.row
        savedState[LINES_KEY] = wheel.lines
    }

    private companion object {
        const val ROW_KEY = "episodeRow"
        const val LINES_KEY = "episodeLines"
    }
}

private fun withShow(
    show: Flow<Either<PodcastError, Show>>,
    episode: Episode,
    needsWifi: Flow<Boolean>,
): Flow<Either<PodcastError, EpisodeContent>> = combine(show, needsWifi) { found, wifi ->
    either { episodeContent(found.bind(), episode, wifi) }
}

/**
 * An episode of the library: Play when it is unplayed or played, from 0:00, and Resume and Play from Start when it is
 * partly played, with the time left (ADR-031, Played, partly played and unplayed). An episode with no download while
 * episodes [needWifi] has Needs Wi-Fi in place of its rows, and gets Play back once Wi-Fi or the setting allows it
 * (frame 36). One partly downloaded plays as far as it is downloaded, so it keeps its rows.
 */
internal fun episodeContent(show: Show, episode: Episode, needWifi: Boolean = false): EpisodeContent {
    val partlyPlayed = episode.playedState == PlayedState.PartlyPlayed
    return EpisodeContent(
        showTitle = show.title,
        rows = when {
            needWifi && episode.download == DownloadState.NotDownloaded -> listOf(EpisodeRow.NeedsWifi)
            partlyPlayed -> listOf(EpisodeRow.Resume, EpisodeRow.PlayFromStart)
            else -> listOf(EpisodeRow.Play)
        },
        info = EpisodeInfo(
            publishedAt = episode.publishedAt,
            length = episode.length,
            left = episode.length?.takeIf {
                partlyPlayed
            }?.let { (it - episode.position).coerceAtLeast(Duration.ZERO) },
        ),
        description = episode.description?.takeIf { it.isNotBlank() },
    )
}

/**
 * An episode from search that isn't in the library (ADR-031, Search): it has no position and no download, so Play is
 * its only row, or Needs Wi-Fi while episodes [needWifi].
 */
internal fun foundEpisodeContent(
    show: DirectoryShow,
    episode: DirectoryEpisode,
    needWifi: Boolean = false,
): EpisodeContent = EpisodeContent(
    showTitle = show.title,
    rows = listOf(if (needWifi) EpisodeRow.NeedsWifi else EpisodeRow.Play),
    info = EpisodeInfo(publishedAt = episode.publishedAt, length = episode.length, left = null),
    description = episode.description?.takeIf { it.isNotBlank() },
)

internal fun PlaybackError.toEpisodePlayFailure(): EpisodePlayFailure = when (this) {
    PlaybackError.PlayerUnavailable -> EpisodePlayFailure.PlayerUnavailable

    PlaybackError.StorageFull -> EpisodePlayFailure.StorageFull

    PlaybackError.MobileDataOff -> EpisodePlayFailure.MobileDataOff

    // The podcasts couldn't give the episode: it is gone, or couldn't be read. The others are the library's, which an
    // episode never reads, or the state's: the nearest thing to say if one ever came.
    is PlaybackError.Podcasts,
    is PlaybackError.Library,
    is PlaybackError.ItemNotInSource,
    is PlaybackError.NothingToPlay,
    is PlaybackError.Unplayable,
    PlaybackError.NoConnection,
    -> EpisodePlayFailure.EpisodeUnavailable
}
