package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A row of the Episode screen: what center on it does. */
internal enum class EpisodeRow {
    /** From 0:00: an unplayed episode, a played one, or one of a show opened from search. */
    Play,

    /** From where the user stopped, for one partly played. */
    Resume,

    /** From 0:00, for one partly played. */
    PlayFromStart,
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

    /** Center: plays the episode as the highlighted row says. */
    abstract fun onCenter()
}

/** The episode is the one of the entry's key, given when the entry creates the ViewModel (see [ShowViewModelImpl]). */
@HiltViewModel(assistedFactory = EpisodeViewModelImpl.Factory::class)
internal class EpisodeViewModelImpl @AssistedInject constructor(
    @Assisted episodeKey: Long,
    private val savedState: SavedStateHandle,
    podcastsRepository: PodcastsRepository,
) : EpisodeViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val uiState: StateFlow<ListUiState<EpisodeContent>> =
        podcastsRepository.observeEpisode(EpisodeId(episodeKey))
            .flatMapLatest { episode ->
                episode.fold(
                    ifLeft = { flowOf(Either.Left(it)) },
                    ifRight = { found -> withShow(podcastsRepository.observeShow(found.showId), found) },
                )
            }
            .asListState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    override val wheel: StateFlow<EpisodeWheel> = combine(
        savedState.getStateFlow(ROW_KEY, 0),
        savedState.getStateFlow(LINES_KEY, 0),
    ) { row, lines -> EpisodeWheel(row, lines) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentWheel())

    private var scrollableLines = 0

    override fun onTurn(steps: Int) {
        val rowCount = (uiState.value as? ListUiState.Loaded)?.value?.rows?.size ?: return
        save(currentWheel().turned(steps, rowCount, scrollableLines))
    }

    override fun onScrollableLinesChange(lines: Int) {
        scrollableLines = lines.coerceAtLeast(0)
        // A larger font or a shorter text leaves fewer lines to scroll: the text stays at its end.
        val wheel = currentWheel()
        if (wheel.lines > scrollableLines) save(wheel.copy(lines = scrollableLines))
    }

    override fun onCenter() {
        // Playing comes with playback/09 and podcasts/07: Play and Play from Start from 0:00, Resume from the
        // episode's position. Until then center on a row does nothing.
    }

    private fun currentWheel() = EpisodeWheel(savedState[ROW_KEY] ?: 0, savedState[LINES_KEY] ?: 0)

    private fun save(wheel: EpisodeWheel) {
        savedState[ROW_KEY] = wheel.row
        savedState[LINES_KEY] = wheel.lines
    }

    @AssistedFactory
    interface Factory {
        fun create(episodeKey: Long): EpisodeViewModelImpl
    }

    private companion object {
        const val ROW_KEY = "episodeRow"
        const val LINES_KEY = "episodeLines"
    }
}

private fun withShow(
    show: Flow<Either<PodcastError, Show>>,
    episode: Episode,
): Flow<Either<PodcastError, EpisodeContent>> = show.map { found -> either { episodeContent(found.bind(), episode) } }

/**
 * An episode of the library: Play when it is unplayed or played, from 0:00, and Resume and Play from Start when it is
 * partly played, with the time left (ADR-031, Played, partly played and unplayed).
 */
internal fun episodeContent(show: Show, episode: Episode): EpisodeContent {
    val partlyPlayed = episode.playedState == PlayedState.PartlyPlayed
    return EpisodeContent(
        showTitle = show.title,
        rows = if (partlyPlayed) listOf(EpisodeRow.Resume, EpisodeRow.PlayFromStart) else listOf(EpisodeRow.Play),
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
 * An episode of a show opened from search (frame 32): it isn't in the library, so it has no position, and Play is its
 * only row. Search's own ViewModel builds it, with podcasts/08.
 */
internal fun foundEpisodeContent(show: DirectoryShow, episode: DirectoryEpisode): EpisodeContent = EpisodeContent(
    showTitle = show.title,
    rows = listOf(EpisodeRow.Play),
    info = EpisodeInfo(publishedAt = episode.publishedAt, length = episode.length, left = null),
    description = episode.description?.takeIf { it.isNotBlank() },
)
