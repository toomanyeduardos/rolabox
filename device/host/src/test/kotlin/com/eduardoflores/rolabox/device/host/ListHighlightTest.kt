package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ListHighlightTest {
    private val highlight = ListHighlight(0)

    @Test
    fun move_goesForwardAndBackByTheSteps() {
        highlight.move(steps = 3, rowCount = 8)
        highlight.move(steps = -1, rowCount = 8)

        assertEquals(2, highlight.index)
    }

    @Test
    fun move_stopsAtTheLastRow() {
        highlight.move(steps = 20, rowCount = 8)

        assertEquals(7, highlight.index)
    }

    @Test
    fun move_stopsAtTheFirstRow() {
        highlight.move(steps = 2, rowCount = 8)
        highlight.move(steps = -20, rowCount = 8)

        assertEquals(0, highlight.index)
    }

    @Test
    fun move_onAnEmptyList_staysAtTheFirstRow() {
        highlight.move(steps = 1, rowCount = 0)

        assertEquals(0, highlight.index)
    }

    @Test
    fun move_whenTheListGotShorter_comesBackInsideIt() {
        highlight.move(steps = 7, rowCount = 8)

        highlight.move(steps = 0, rowCount = 3)

        assertEquals(2, highlight.index)
    }

    // Search's results: a label, two shows, a label, two episodes.
    private val labels = setOf(0, 3)
    private val isSelectable = { index: Int -> index !in labels }

    @Test
    fun move_ofZeroSteps_fromALabel_goesToTheFirstRowAfterIt() {
        highlight.move(steps = 0, rowCount = 6, isSelectable = isSelectable)

        assertEquals(1, highlight.index)
    }

    @Test
    fun move_forward_skipsALabel() {
        highlight.move(steps = 0, rowCount = 6, isSelectable = isSelectable)

        highlight.move(steps = 2, rowCount = 6, isSelectable = isSelectable)

        assertEquals(4, highlight.index)
    }

    @Test
    fun move_back_skipsALabel() {
        highlight.move(steps = 4, rowCount = 6)

        highlight.move(steps = -1, rowCount = 6, isSelectable = isSelectable)

        assertEquals(2, highlight.index)
    }

    @Test
    fun move_back_stopsAtTheFirstRowThatCanBeSelected() {
        highlight.move(steps = 4, rowCount = 6, isSelectable = isSelectable)

        highlight.move(steps = -20, rowCount = 6, isSelectable = isSelectable)

        assertEquals(1, highlight.index)
    }

    @Test
    fun move_fromALabel_takesOneStepToTheRowsOnEitherSide() {
        highlight.move(steps = 3, rowCount = 6)
        highlight.move(steps = 1, rowCount = 6, isSelectable = isSelectable)
        assertEquals(4, highlight.index)

        highlight.move(steps = -1, rowCount = 6)
        highlight.move(steps = -1, rowCount = 6, isSelectable = isSelectable)
        assertEquals(2, highlight.index)
    }

    @Test
    fun move_fromALabelAtTheEnd_goesToTheLastRowThatCanBeSelected() {
        highlight.move(steps = 3, rowCount = 6)

        highlight.move(steps = 0, rowCount = 4, isSelectable = isSelectable)

        assertEquals(2, highlight.index)
    }

    @Test
    fun move_whenNoRowCanBeSelected_staysInsideTheList() {
        highlight.move(steps = 5, rowCount = 6)

        highlight.move(steps = 1, rowCount = 2, isSelectable = { false })

        assertEquals(1, highlight.index)
    }

    @Test
    fun saver_restoresTheRowTheUserLeft() {
        highlight.move(steps = 3, rowCount = 8)
        val scope = SaverScope { true }

        val saved = with(ListHighlight.Saver) { scope.save(highlight) }
        val restored = ListHighlight.Saver.restore(checkNotNull(saved))

        assertEquals(3, restored?.index)
    }
}
