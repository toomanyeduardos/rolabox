package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.rememberListHighlight

/**
 * A list on the display, driven by the wheel (ADR-018): turning moves the highlight, which stops at
 * the first and last rows, and center reports the index of the highlighted row to [onCenter]. It does
 * nothing while there are no rows to highlight.
 *
 * The highlight is saved with the entry that calls this (ADR-018, rule 7), and belongs to the host's
 * holder. [emptyText] is shown when the list has no rows.
 */
@Composable
internal fun WheelList(state: ListUiState<DeviceListRow>, emptyText: String, onCenter: (index: Int) -> Unit) {
    val highlight = rememberListHighlight()
    val rowCount = (state as? ListUiState.Loaded)?.items?.size ?: 0
    HandleWheelEvents { event ->
        when {
            rowCount == 0 -> Unit
            event is WheelEvent.Turn -> highlight.move(event.steps, rowCount)
            event == WheelEvent.Center -> onCenter(highlight.index.coerceAtMost(rowCount - 1))
        }
    }
    MusicList(state = state, highlightedIndex = highlight.index, emptyText = emptyText)
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun MusicList(state: ListUiState<DeviceListRow>, highlightedIndex: Int, emptyText: String) {
    when (state) {
        // The library is in memory for now, so this is a frame. Nothing to say until it is slow.
        ListUiState.Loading -> Unit

        is ListUiState.Loaded -> if (state.items.isEmpty()) {
            DeviceMessage(emptyText)
        } else {
            DeviceList(rows = state.items, highlightedIndex = highlightedIndex.coerceIn(0, state.items.lastIndex))
        }

        ListUiState.Failed -> DeviceMessage(stringResource(R.string.music_library_unavailable))
    }
}
