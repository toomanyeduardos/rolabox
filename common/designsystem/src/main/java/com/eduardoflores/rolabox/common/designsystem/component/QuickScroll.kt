package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelAcceleration
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the letter stays after the last fast turn. */
internal const val QUICK_SCROLL_HIDE_MILLIS = 600L

/** The fewest rows a list has for the letter to show: about three screens of rows at 1.0x. */
internal const val QUICK_SCROLL_MIN_ROWS = 24

/** What a row whose label doesn't start with a letter shows: a digit, a symbol or nothing. */
internal const val QUICK_SCROLL_OTHER = "#"

/**
 * The letter that a list sorted by name shows over its rows while the wheel spins fast, for a
 * [DeviceLetterOverlay]. A fast turn shows the first letter of the highlighted row, a slower turn
 * while it shows keeps it up to date, and it goes away [QUICK_SCROLL_HIDE_MILLIS] after the last fast
 * turn. A list shorter than [QUICK_SCROLL_MIN_ROWS] never shows it.
 *
 * Fast is read from the turn's steps, which have the wheel's acceleration in them (ADR-018, rule 4).
 */
@Stable
class QuickScroll internal constructor(private val scope: CoroutineScope) {
    /** The letter to show, or null when none is shown. */
    var letter: String? by mutableStateOf(null)
        private set

    private var hide: Job? = null

    /** The list was turned by [steps], and the highlight is now on the row labelled [highlightedLabel]. */
    fun turned(steps: Int, highlightedLabel: String, rowCount: Int) {
        if (rowCount < QUICK_SCROLL_MIN_ROWS) return
        when {
            abs(steps) >= WheelAcceleration.FAST_TURN_STEPS -> {
                letter = quickScrollLetterOf(highlightedLabel)
                hide?.cancel()
                hide = scope.launch {
                    delay(QUICK_SCROLL_HIDE_MILLIS)
                    letter = null
                }
            }

            // A slower turn doesn't keep the letter up, but it doesn't leave it on the wrong row either.
            letter != null -> letter = quickScrollLetterOf(highlightedLabel)
        }
    }
}

/** A [QuickScroll] for the list that calls this. Its letter is gone when the list leaves. */
@Composable
fun rememberQuickScroll(): QuickScroll {
    val scope = rememberCoroutineScope()
    return remember(scope) { QuickScroll(scope) }
}

/**
 * The first character of [label], in upper case, or [QUICK_SCROLL_OTHER] when it isn't a letter. "The"
 * isn't skipped, because the library sorts by the whole name: "The Beatles" is with the T's.
 */
internal fun quickScrollLetterOf(label: String): String {
    val first = label.trimStart().firstOrNull()
    return if (first != null && first.isLetter()) first.uppercaseChar().toString() else QUICK_SCROLL_OTHER
}
