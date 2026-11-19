package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The highlight of a device screen's list: the index of the row the wheel is on. It belongs to the
 * screen that remembers it, and never leaves it (ADR-018, rule 7).
 */
@Stable
class ListHighlight internal constructor(index: Int) {
    var index by mutableIntStateOf(index)
        internal set

    /**
     * Moves the highlight by the [steps] of a turn, and stops at the first and last of [rowCount] rows.
     *
     * A row that [isSelectable] says no to, such as a section label, is skipped: each step goes to the
     * next row that can be selected, and the highlight stops at the first and last of those. A highlight
     * that is on a row that can't be selected, such as when the rows changed under it, goes to the next
     * one that can, or to the last when none follows, and a move of 0 steps does only that, for a screen
     * whose rows just changed. When no
     * row can be selected, the highlight stays where it is.
     */
    fun move(steps: Int, rowCount: Int, isSelectable: (Int) -> Boolean = { true }) {
        val selectable = (0 until rowCount).filter(isSelectable)
        if (selectable.isEmpty()) {
            index = index.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
            return
        }
        val before = selectable.count { it < index }
        // From a row that can't be selected, the first step forward lands on the next one that can.
        val from = if (index in selectable || steps <= 0) before else before - 1
        index = selectable[(from + steps).coerceIn(0, selectable.lastIndex)]
    }

    internal companion object {
        val Saver = Saver<ListHighlight, Int>(save = { it.index }, restore = { ListHighlight(it) })
    }
}

/** A list's highlight, starting at the first row, saved with the entry of the screen that calls this. */
@Composable
fun rememberListHighlight(): ListHighlight = rememberSaveable(saver = ListHighlight.Saver) { ListHighlight(0) }
