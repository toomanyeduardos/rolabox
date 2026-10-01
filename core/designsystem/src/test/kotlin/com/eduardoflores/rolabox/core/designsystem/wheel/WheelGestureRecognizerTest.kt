package com.eduardoflores.rolabox.core.designsystem.wheel

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelGestureRecognizerTest {
    private val recognizer = WheelGestureRecognizer(centerRadius = CENTER, ringRadius = RING)

    private fun x(degrees: Int, radius: Float = MID) = (radius * cos(Math.toRadians(degrees.toDouble()))).toFloat()

    private fun y(degrees: Int, radius: Float = MID) = (radius * sin(Math.toRadians(degrees.toDouble()))).toFloat()

    private fun pressAt(degrees: Int, radius: Float = MID): WheelEvent? {
        recognizer.down(x(degrees, radius), y(degrees, radius), 0)
        return recognizer.up()
    }

    /** Presses the ring at [degrees] and holds past the timeout, returning the hold's start. */
    private fun holdAt(degrees: Int): WheelEvent? {
        recognizer.down(x(degrees), y(degrees), 0)
        return recognizer.holdTimeout()
    }

    // Press versus drag

    @Test
    fun `a tap on each side of the ring presses that button`() {
        assertEquals(WheelEvent.Menu, pressAt(-90))
        assertEquals(WheelEvent.Next, pressAt(0))
        assertEquals(WheelEvent.PlayPause, pressAt(90))
        assertEquals(WheelEvent.Previous, pressAt(180))
    }

    @Test
    fun `the diagonals fall to the side they are nearer`() {
        assertEquals(WheelEvent.Next, pressAt(-44))
        assertEquals(WheelEvent.Menu, pressAt(-46))
        assertEquals(WheelEvent.PlayPause, pressAt(46))
        assertEquals(WheelEvent.Previous, pressAt(136))
    }

    @Test
    fun `a tap on the center is the center button`() {
        recognizer.down(0f, 0f, 0)
        assertEquals(WheelEvent.Center, recognizer.up())
    }

    @Test
    fun `a touch outside the ring is ignored`() {
        assertNull(pressAt(-90, radius = RING + 1))
    }

    @Test
    fun `a small wobble on the ring is still a press`() {
        recognizer.down(x(-90), y(-90), 0)
        assertNull(recognizer.move(x(-86), y(-86), 10))
        assertEquals(WheelEvent.Menu, recognizer.up())
    }

    @Test
    fun `dragging around the ring turns and does not press`() {
        recognizer.down(x(-90), y(-90), 0)
        var steps = 0
        var time = 0L
        for (angle in -89..0) {
            time += SLOW_MILLIS_PER_DEGREE
            steps += recognizer.move(x(angle), y(angle), time)?.steps ?: 0
        }
        assertEquals(6, steps)
        assertNull(recognizer.up())
    }

    @Test
    fun `dragging off the center button does not turn the wheel`() {
        recognizer.down(0f, 0f, 0)
        assertNull(recognizer.move(x(0), y(0), 10))
        assertNull(recognizer.move(x(90), y(90), 20))
        assertNull(recognizer.up())
    }

    @Test
    fun `lifting off the center button after sliding out is not a press`() {
        recognizer.down(0f, 0f, 0)
        recognizer.move(x(0, MID), y(0, MID), 10)
        assertNull(recognizer.up())
    }

    // Holds

    @Test
    fun `holding MENU reports the hold once and nothing on release`() {
        assertEquals(WheelEvent.HoldMenu, holdAt(-90))
        assertNull(recognizer.up())
    }

    @Test
    fun `holding previous reports held, then released, and no press`() {
        assertEquals(WheelEvent.HoldPrevious(HoldState.Held), holdAt(180))
        assertEquals(WheelEvent.HoldPrevious(HoldState.Released), recognizer.up())
    }

    @Test
    fun `holding next reports held, then released, and no press`() {
        assertEquals(WheelEvent.HoldNext(HoldState.Held), holdAt(0))
        assertEquals(WheelEvent.HoldNext(HoldState.Released), recognizer.up())
    }

    @Test
    fun `play and pause and the center have no hold`() {
        recognizer.down(x(90), y(90), 0)
        assertFalse(recognizer.canHold)
        assertNull(recognizer.holdTimeout())
        assertEquals(WheelEvent.PlayPause, recognizer.up())

        recognizer.down(0f, 0f, 0)
        assertFalse(recognizer.canHold)
        assertNull(recognizer.holdTimeout())
        assertEquals(WheelEvent.Center, recognizer.up())
    }

    @Test
    fun `a hold can start only once`() {
        recognizer.down(x(0), y(0), 0)
        assertTrue(recognizer.canHold)
        recognizer.holdTimeout()
        assertFalse(recognizer.canHold)
        assertNull(recognizer.holdTimeout())
    }

    @Test
    fun `turning before the hold time cancels the hold`() {
        recognizer.down(x(-90), y(-90), 0)
        var time = 0L
        for (angle in -89..-60) {
            time += SLOW_MILLIS_PER_DEGREE
            recognizer.move(x(angle), y(angle), time)
        }
        assertFalse(recognizer.canHold)
        assertNull(recognizer.holdTimeout())
        assertNull(recognizer.up())
    }

    @Test
    fun `moving while held reports no turns, and the release comes on lift`() {
        holdAt(0)
        var turns = 0
        var time = 0L
        for (angle in 1..90) {
            time += SLOW_MILLIS_PER_DEGREE
            if (recognizer.move(x(angle), y(angle), time) != null) turns++
        }
        assertEquals(0, turns)
        assertEquals(WheelEvent.HoldNext(HoldState.Released), recognizer.up())
    }

    @Test
    fun `cancelling a held button releases it once`() {
        holdAt(180)
        assertEquals(WheelEvent.HoldPrevious(HoldState.Released), recognizer.cancel())
        assertNull(recognizer.cancel())
        assertNull(recognizer.up())
    }

    @Test
    fun `cancelling a press reports nothing`() {
        recognizer.down(x(0), y(0), 0)
        assertNull(recognizer.cancel())
    }

    @Test
    fun `a gesture after a hold starts clean`() {
        holdAt(0)
        recognizer.up()
        assertEquals(WheelEvent.Menu, pressAt(-90))
    }

    // Haptics

    @Test
    fun `a turn ticks, a press and the start of a hold are stronger, a release is silent`() {
        assertEquals(WheelHaptic.Tick, WheelEvent.Turn(1).haptic())
        assertEquals(WheelHaptic.Press, WheelEvent.Center.haptic())
        assertEquals(WheelHaptic.Press, WheelEvent.Menu.haptic())
        assertEquals(WheelHaptic.Press, WheelEvent.PlayPause.haptic())
        assertEquals(WheelHaptic.Press, WheelEvent.HoldMenu.haptic())
        assertEquals(WheelHaptic.Press, WheelEvent.HoldNext(HoldState.Held).haptic())
        assertNull(WheelEvent.HoldNext(HoldState.Released).haptic())
        assertNull(WheelEvent.HoldPrevious(HoldState.Released).haptic())
    }

    @Test
    fun `a turn ticks once per step, in either direction`() {
        assertEquals(1, WheelEvent.Turn(1).tickCount())
        assertEquals(8, WheelEvent.Turn(8).tickCount())
        assertEquals(4, WheelEvent.Turn(-4).tickCount())
        assertEquals(0, WheelEvent.Menu.tickCount())
    }

    private companion object {
        const val CENTER = 49f
        const val RING = 134f
        const val MID = 92f
        const val SLOW_MILLIS_PER_DEGREE = 10L
    }
}
