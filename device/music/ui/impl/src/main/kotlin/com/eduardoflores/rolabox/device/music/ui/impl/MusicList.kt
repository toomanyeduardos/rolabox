package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceLetterOverlay
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.rememberQuickScroll
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandlePlayPause
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.rememberListHighlight

/**
 * A list on the display, driven by the wheel (ADR-018): turning moves the highlight, which stops at
 * the first and last rows, and center reports the index of the highlighted row to [onCenter]. It does
 * nothing while there are no rows to highlight. [onPlayPause] is for a list whose rows can be played: ⏯
 * reports the highlighted row to it, and on every other list ⏯ isn't the screen's (ADR-018, rule 5).
 *
 * The highlight is saved with the entry that calls this (ADR-018, rule 7), and belongs to the host's
 * holder. [emptyText] is shown when the list has no rows.
 *
 * [sortedByName] is for a list whose rows are sorted by their labels, such as the artists: while it is
 * turned fast, the first letter of the highlighted row is shown over it. Menus and an album's songs
 * aren't sorted by name, and never show it.
 */
@Composable
internal fun WheelList(
    state: ListUiState<DeviceListRow>,
    emptyText: String,
    onCenter: (index: Int) -> Unit,
    onPlayPause: ((index: Int) -> Unit)? = null,
    sortedByName: Boolean = false,
) {
    val highlight = rememberListHighlight()
    val quickScroll = rememberQuickScroll()
    val rows = (state as? ListUiState.Loaded)?.items.orEmpty()
    val rowCount = rows.size
    // With no row to play, ⏯ is left to the device, which plays or pauses what is loaded.
    HandlePlayPause(enabled = onPlayPause != null && rowCount > 0) {
        onPlayPause?.invoke(highlight.index.coerceAtMost(rowCount - 1))
    }
    HandleWheelEvents { event ->
        when {
            rowCount == 0 -> Unit

            event is WheelEvent.Turn -> {
                highlight.move(event.steps, rowCount)
                if (sortedByName) quickScroll.turned(event.steps, rows[highlight.index].label, rowCount)
            }

            event == WheelEvent.Center -> onCenter(highlight.index.coerceAtMost(rowCount - 1))
        }
    }
    MusicList(state = state, highlightedIndex = highlight.index, emptyText = emptyText, letter = quickScroll.letter)
}

/**
 * The stateless list that previews and screenshot tests call (ADR-021, rule 4). [letter] is shown over
 * the rows while the list is turned fast, and null shows none.
 */
@Composable
internal fun MusicList(
    state: ListUiState<DeviceListRow>,
    highlightedIndex: Int,
    emptyText: String,
    letter: String? = null,
) {
    when (state) {
        // The library is in memory for now, so this is a frame. Nothing to say until it is slow.
        ListUiState.Loading -> Unit

        is ListUiState.Loaded -> if (state.items.isEmpty()) {
            DeviceMessage(emptyText)
        } else {
            Box {
                DeviceList(rows = state.items, highlightedIndex = highlightedIndex.coerceIn(0, state.items.lastIndex))
                DeviceLetterOverlay(letter)
            }
        }

        ListUiState.Failed -> DeviceMessage(stringResource(R.string.music_library_unavailable))
    }
}
