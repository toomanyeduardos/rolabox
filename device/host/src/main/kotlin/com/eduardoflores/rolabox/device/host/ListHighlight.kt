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
        private set

    /** Moves the highlight by the [steps] of a turn, and stops at the first and last of [rowCount] rows. */
    fun move(steps: Int, rowCount: Int) {
        index = (index + steps).coerceIn(0, (rowCount - 1).coerceAtLeast(0))
    }

    internal companion object {
        val Saver = Saver<ListHighlight, Int>(save = { it.index }, restore = { ListHighlight(it) })
    }
}

/** A list's highlight, starting at the first row, saved with the entry of the screen that calls this. */
@Composable
fun rememberListHighlight(): ListHighlight = rememberSaveable(saver = ListHighlight.Saver) { ListHighlight(0) }
