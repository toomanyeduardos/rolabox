package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState

/**
 * The index of the episode a list of a show's episodes, in [order], opens on (ADR-043, Where the list opens): for
 * oldest first, the first episode after the last one played, which is the one the user is partly through or the next
 * to start, the first episode when none is played, and the last when it is played. `null` for newest first, and for no
 * episodes, which open on the list's first row. [played] is each episode's state, in the list's order.
 */
internal fun startingEpisodeOf(played: List<PlayedState>, order: EpisodeOrder): Int? {
    if (order != EpisodeOrder.Oldest || played.isEmpty()) return null
    val lastPlayed = played.indexOfLast { it == PlayedState.Played }
    return (lastPlayed + 1).coerceAtMost(played.lastIndex)
}

/**
 * The order to list a feed's episodes in, as indices into [publishedAt], each episode's date in the feed's order:
 * newest first, and those with no date last (ADR-031, Feeds). Oldest first reverses the dated ones, and the undated
 * ones stay last, as the library lists them (ADR-043).
 */
internal fun listedIndices(publishedAt: List<Any?>, order: EpisodeOrder): List<Int> {
    val indices = publishedAt.indices.toList()
    if (order == EpisodeOrder.Newest) return indices
    val (dated, undated) = indices.partition { publishedAt[it] != null }
    return dated.reversed() + undated
}
