package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceLetterOverlay
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.rememberQuickScroll
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.rememberListHighlight
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * A podcasts list on the display, driven by the wheel (ADR-018): turning moves the highlight, which stops at the first
 * and last rows, and center reports the index of the highlighted row to [onCenter]. [message] is shown under the rows.
 *
 * The highlight is saved with the entry that calls this (ADR-018, rule 7), and belongs to the host's holder.
 *
 * [sortedFrom] is the index of the first row of a list sorted by its labels, after the rows that come first, such as
 * Search: while it is turned fast, the first letter of the highlighted row is shown over it. `null` for a list that
 * isn't sorted by name, such as a show's episodes.
 */
@Composable
internal fun PodcastsWheelList(
    state: ListUiState<List<DeviceListRow>>,
    onCenter: (index: Int) -> Unit,
    sortedFrom: Int? = null,
    message: DeviceListMessage? = null,
) {
    val highlight = rememberListHighlight()
    val quickScroll = rememberQuickScroll()
    val rows = (state as? ListUiState.Loaded)?.value.orEmpty()
    val rowCount = rows.size
    HandleWheelEvents { event ->
        when {
            rowCount == 0 -> Unit

            event is WheelEvent.Turn -> {
                highlight.move(event.steps, rowCount)
                if (sortedFrom != null && highlight.index >= sortedFrom) {
                    quickScroll.turned(event.steps, rows[highlight.index].label, rowCount - sortedFrom)
                }
            }

            event == WheelEvent.Center -> onCenter(highlight.index.coerceAtMost(rowCount - 1))
        }
    }
    PodcastsList(state = state, highlightedIndex = highlight.index, message = message, letter = quickScroll.letter)
}

/**
 * The stateless list that previews and screenshot tests call (ADR-021, rule 4). [letter] is shown over the rows while
 * the list is turned fast, and null shows none. While it loads, Loading is shown once [loadingDelay] has passed.
 */
@Composable
internal fun PodcastsList(
    state: ListUiState<List<DeviceListRow>>,
    highlightedIndex: Int,
    message: DeviceListMessage? = null,
    letter: String? = null,
    loadingDelay: Duration = LoadingDelay,
) {
    when (state) {
        ListUiState.Loading -> PodcastsLoading(loadingDelay)

        is ListUiState.Loaded -> Box {
            val rows = state.value
            DeviceList(
                rows = rows,
                highlightedIndex = highlightedIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)),
                message = message,
            )
            DeviceLetterOverlay(letter)
        }

        ListUiState.Failed -> DeviceMessage(stringResource(R.string.podcasts_unavailable))

        ListUiState.Gone -> DeviceMessage(stringResource(R.string.podcasts_no_longer_here))

        ListUiState.NoConnection,
        ListUiState.FeedUnreadable,
        ListUiState.NotInFeed,
        -> FromSearchMessage(state)
    }
}

/**
 * How long a screen stays empty before it says Loading. The library's screens read the database, which takes a frame or
 * two, so they never show it. A show or an episode from search reads its feed from the network, which on a slow
 * connection takes seconds.
 */
internal val LoadingDelay = 300.milliseconds

/** Loading, centered, once [delay] has passed: a screen waiting for its podcasts is never blank for long. */
@Composable
internal fun PodcastsLoading(delay: Duration = LoadingDelay) {
    var shown by remember { mutableStateOf(delay <= Duration.ZERO) }
    LaunchedEffect(delay) {
        delay(delay)
        shown = true
    }
    if (shown) DeviceMessage(stringResource(R.string.podcasts_loading))
}

/** Why a show or an episode from search, read from its feed, can't be shown (ADR-031, Search). */
@Composable
internal fun FromSearchMessage(state: ListUiState<*>) {
    val (title, body) = when (state) {
        ListUiState.NoConnection -> R.string.podcasts_search_offline_title to R.string.podcasts_search_offline_body
        ListUiState.NotInFeed -> R.string.podcasts_not_in_feed_title to R.string.podcasts_not_in_feed_body
        else -> R.string.podcasts_feed_unreadable_title to R.string.podcasts_feed_unreadable_body
    }
    DeviceMessage(stringResource(title), stringResource(body))
}
