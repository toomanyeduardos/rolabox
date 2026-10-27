package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long the highlighted row's text rests at its start before it scrolls. */
internal const val ROW_SCROLL_START_WAIT_MILLIS = 1_500L

/** How long the highlighted row's text rests at its end before it starts again. */
internal const val ROW_SCROLL_END_WAIT_MILLIS = 1_500L

/** How fast the highlighted row's text scrolls, per second. */
internal val RowScrollSpeed = 30.dp

private const val NANOS_PER_MILLI = 1_000_000f
private const val MILLIS_PER_SECOND = 1_000f

/**
 * Scrolls a row's text that is [overflow] px wider than its row (ADR-018, rule 14): it rests at its
 * start, moves at [pxPerSecond] until its end meets the row's end, rests there, jumps back to its
 * start, and goes around again until it is cancelled. [onOffset] gets how far the text is moved, 0 at
 * its start. A text that fits doesn't move, and nothing is reported for it.
 *
 * It runs until it is cancelled, which is when the highlight moves. Its frames are those of an
 * infinite animation, so a Compose test cancels them instead of waiting for them.
 */
internal suspend fun scrollRowText(overflow: Float, pxPerSecond: Float, onOffset: (Float) -> Unit) {
    if (overflow <= 0f) return
    val scrollMillis = overflow / pxPerSecond * MILLIS_PER_SECOND
    while (true) {
        delay(ROW_SCROLL_START_WAIT_MILLIS)
        val start = withInfiniteAnimationFrameNanos { it }
        do {
            val elapsed = (withInfiniteAnimationFrameNanos { it } - start) / NANOS_PER_MILLI
            val offset = overflow * (elapsed / scrollMillis).coerceAtMost(1f)
            onOffset(offset)
        } while (offset < overflow)
        delay(ROW_SCROLL_END_WAIT_MILLIS)
        onOffset(0f)
    }
}
