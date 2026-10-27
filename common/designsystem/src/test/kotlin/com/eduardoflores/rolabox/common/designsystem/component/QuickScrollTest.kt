package com.eduardoflores.rolabox.common.designsystem.component

import com.eduardoflores.rolabox.common.designsystem.wheel.WheelAcceleration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the letter over a list sorted by name shows and goes away, and which letter it is, on a virtual clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickScrollTest {
    private fun TestScope.quickScroll() = QuickScroll(backgroundScope)

    @Test
    fun `a slow turn shows no letter`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = 1, highlightedLabel = "Maren Holt", rowCount = LONG)
        quickScroll.turned(steps = SLOW, highlightedLabel = "Maren Holt", rowCount = LONG)
        assertNull(quickScroll.letter)
    }

    @Test
    fun `a fast turn shows the first letter of the highlighted row`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = FAST, highlightedLabel = "Maren Holt", rowCount = LONG)
        assertEquals("M", quickScroll.letter)
    }

    @Test
    fun `a fast turn backwards shows it too`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = -FAST, highlightedLabel = "Blue Harbor", rowCount = LONG)
        assertEquals("B", quickScroll.letter)
    }

    @Test
    fun `a short list never shows it`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = FAST, highlightedLabel = "Maren Holt", rowCount = QUICK_SCROLL_MIN_ROWS - 1)
        assertNull(quickScroll.letter)
    }

    @Test
    fun `it goes away after the last fast turn`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = FAST, highlightedLabel = "Maren Holt", rowCount = LONG)
        advanceTimeBy(QUICK_SCROLL_HIDE_MILLIS - 1)
        assertEquals("M", quickScroll.letter)
        advanceTimeBy(2)
        assertNull(quickScroll.letter)
    }

    @Test
    fun `each fast turn keeps it up for longer`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = FAST, highlightedLabel = "Maren Holt", rowCount = LONG)
        advanceTimeBy(QUICK_SCROLL_HIDE_MILLIS - 1)
        quickScroll.turned(steps = FAST, highlightedLabel = "Nadia Frost", rowCount = LONG)
        advanceTimeBy(QUICK_SCROLL_HIDE_MILLIS - 1)
        assertEquals("N", quickScroll.letter)
        advanceTimeBy(2)
        assertNull(quickScroll.letter)
    }

    @Test
    fun `a slower turn while it shows changes the letter but doesn't keep it up`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = FAST, highlightedLabel = "Maren Holt", rowCount = LONG)
        advanceTimeBy(QUICK_SCROLL_HIDE_MILLIS / 2)
        quickScroll.turned(steps = SLOW, highlightedLabel = "Nadia Frost", rowCount = LONG)
        assertEquals("N", quickScroll.letter)
        advanceTimeBy(QUICK_SCROLL_HIDE_MILLIS / 2 + 1)
        assertNull(quickScroll.letter)
    }

    @Test
    fun `a slow turn after it went away doesn't bring it back`() = runTest {
        val quickScroll = quickScroll()
        quickScroll.turned(steps = FAST, highlightedLabel = "Maren Holt", rowCount = LONG)
        advanceTimeBy(QUICK_SCROLL_HIDE_MILLIS + 1)
        quickScroll.turned(steps = 1, highlightedLabel = "Nadia Frost", rowCount = LONG)
        assertNull(quickScroll.letter)
    }

    @Test
    fun `the letter is the first character in upper case`() {
        assertEquals("M", quickScrollLetterOf("Maren Holt"))
        assertEquals("A", quickScrollLetterOf("alt-J"))
        assertEquals("É", quickScrollLetterOf("Élan Vital"))
        assertEquals("O", quickScrollLetterOf("  Otis Ray"))
    }

    @Test
    fun `a name that starts with the is with the T's, as the library sorts it`() {
        assertEquals("T", quickScrollLetterOf("The Paper Lanterns"))
    }

    @Test
    fun `a name that starts with a digit or a symbol shows #`() {
        assertEquals("#", quickScrollLetterOf("10,000 Lakes"))
        assertEquals("#", quickScrollLetterOf("!Static"))
        assertEquals("#", quickScrollLetterOf("(Parentheses)"))
        assertEquals("#", quickScrollLetterOf(""))
    }

    private companion object {
        const val FAST = WheelAcceleration.FAST_TURN_STEPS
        const val SLOW = FAST - 1
        const val LONG = QUICK_SCROLL_MIN_ROWS
    }
}
