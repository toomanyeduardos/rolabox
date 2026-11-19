package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.Composable
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListEntry
import com.eduardoflores.rolabox.common.designsystem.component.isSelectable

/*
 * The highlight of a list with a field (ADR-032, rule 5). The field is the list's first row, so in the screen's
 * ListHighlight it is index 0, and the list's rows follow it from index 1. The wheel reaches the field like any
 * row, and skips the rows that can't be selected, such as section labels.
 */

/** The field's index in the highlight of a list with a field. */
private const val FIELD = 0

/** Whether the highlight is on the field. */
val ListHighlight.isOnField: Boolean get() = index == FIELD

/**
 * The index in [rows] of the highlighted row, for `DeviceList`, or -1 when the field is highlighted or the
 * device is [typing], when nothing is (frame 30).
 */
fun ListHighlight.rowIndex(typing: DeviceTyping): Int = if (typing.isTyping || isOnField) -1 else index - 1

/** Moves the highlight by the [steps] of a turn, over the field and the [rows] that can be selected. */
fun ListHighlight.moveWithField(steps: Int, rows: List<DeviceListEntry>) {
    move(steps, rows.size + 1) { it == FIELD || rows.isSelectable(it - 1) }
}

/** Highlights the field, for MENU on a row: the field is then one center away. */
fun ListHighlight.highlightField() {
    index = FIELD
}

/**
 * MENU for a screen with a field (ADR-032, rule 5): on a row it highlights the field, and on the field it is the
 * host's, and goes back one screen. A screen with a field calls this in place of [HandleMenu].
 */
@Composable
fun HandleMenuWithField(highlight: ListHighlight) {
    HandleMenu(enabled = screenTakesMenu(highlight), onMenu = highlight::highlightField)
}

/** Whether a screen with a field takes MENU, to highlight its field: only while a row other than the field is. */
internal fun screenTakesMenu(highlight: ListHighlight): Boolean = !highlight.isOnField

/** When typing ends: highlights the first of the [rows] that can be selected, or the field when there is none. */
fun ListHighlight.highlightAfterTyping(rows: List<DeviceListEntry>) {
    val first = rows.indices.firstOrNull { rows.isSelectable(it) }
    index = if (first == null) FIELD else first + 1
}
