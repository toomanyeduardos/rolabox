package com.eduardoflores.rolabox.core.device

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
}
