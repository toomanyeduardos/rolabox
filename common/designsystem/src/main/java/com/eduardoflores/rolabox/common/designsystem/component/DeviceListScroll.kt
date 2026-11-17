package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Scrolls [state] just enough for the row at [index] to be fully in view, and does nothing when it
 * already is. A row that comes from far away is put at the edge it came from, so a list that is
 * turned forward keeps the highlight at the bottom, as the wheel is moving down. A [top] above
 * [index], the label of the highlighted row's section, is brought into view with it on the way up.
 */
@Composable
internal fun KeepInView(state: LazyListState, index: Int, top: Int = index) {
    LaunchedEffect(state, index) {
        // Nothing is highlighted, so there is nothing to keep in view.
        if (index < 0) return@LaunchedEffect
        state.keepInView(index)
        val label = state.visibleRow(top)
        if (top < index && (label == null || label.offset < state.layoutInfo.viewportStartOffset)) {
            state.scrollToItem(top)
        }
    }
}

/** Scrolls just enough for the row at [index] to be fully in view: see [KeepInView]. */
private suspend fun LazyListState.keepInView(index: Int) {
    val cameFromAbove = index > firstVisibleItemIndex
    var item = visibleRow(index)
    if (item == null) {
        scrollToItem(index)
        item = visibleRow(index) ?: return
        val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (cameFromAbove && item.offset <= layoutInfo.viewportStartOffset && item.size < viewport) {
            scrollBy(-(viewport - item.size).toFloat())
        }
        return
    }
    val info = layoutInfo
    when {
        item.offset < info.viewportStartOffset -> scrollBy((item.offset - info.viewportStartOffset).toFloat())

        item.offset + item.size > info.viewportEndOffset ->
            scrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
    }
}

private fun LazyListState.visibleRow(index: Int): LazyListItemInfo? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
