package com.eduardoflores.rolabox.device.host

import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldListHighlightTest {
    private val results = listOf(
        DeviceListSection("Shows"),
        DeviceListRow("The Daily"),
        DeviceListRow("The Daily Brief"),
        DeviceListSection("Episodes"),
        DeviceListRow("Why the Daily Commute Changed"),
    )

    @Test
    fun afterTyping_withResults_highlightsTheFirstResult_skippingItsLabel() {
        val highlight = ListHighlight(0)

        highlight.highlightAfterTyping(results)

        assertFalse(highlight.isOnField)
        assertEquals(1, highlight.rowIndex(DeviceTyping(false)))
    }

    @Test
    fun afterTyping_withNoResult_highlightsTheField() {
        val highlight = ListHighlight(3)

        highlight.highlightAfterTyping(emptyList())

        assertTrue(highlight.isOnField)
        assertEquals(-1, highlight.rowIndex(DeviceTyping(false)))
    }

    @Test
    fun afterTyping_withOnlyLabels_highlightsTheField() {
        val highlight = ListHighlight(2)

        highlight.highlightAfterTyping(listOf(DeviceListSection("Shows")))

        assertTrue(highlight.isOnField)
    }

    @Test
    fun whileTyping_noRowIsHighlighted() {
        val highlight = ListHighlight(2)

        assertEquals(-1, highlight.rowIndex(DeviceTyping(true)))
    }

    @Test
    fun turn_goesFromTheFieldToTheRows_skippingLabels() {
        val highlight = ListHighlight(0)

        highlight.moveWithField(1, results)
        assertEquals(1, highlight.rowIndex(DeviceTyping(false)))

        highlight.moveWithField(2, results)
        assertEquals(4, highlight.rowIndex(DeviceTyping(false)))
    }

    @Test
    fun turn_backFromTheFirstRow_reachesTheField_andStopsThere() {
        val highlight = ListHighlight(2)

        highlight.moveWithField(-5, results)

        assertTrue(highlight.isOnField)
    }

    @Test
    fun turn_withNoRows_staysOnTheField() {
        val highlight = ListHighlight(0)

        highlight.moveWithField(3, emptyList())

        assertTrue(highlight.isOnField)
    }

    @Test
    fun highlightField_movesFromARowToTheField() {
        val highlight = ListHighlight(5)

        highlight.highlightField()

        assertTrue(highlight.isOnField)
    }

    @Test
    fun theScreenTakesMenu_onlyWhileARowIsHighlighted() {
        assertTrue(screenTakesMenu(ListHighlight(2)))
        assertFalse(screenTakesMenu(ListHighlight(0)))
    }
}
