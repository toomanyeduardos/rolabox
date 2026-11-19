package com.eduardoflores.rolabox.common.designsystem.wheel

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelTurnTrackerTest {
    private val tracker = WheelTurnTracker()

    /** Slow enough that acceleration never applies: 10 ms per degree is under a third of a turn per second. */
    private fun slowTurn(from: Int, to: Int): List<Int> {
        val direction = if (to > from) 1 else -1
        var time = 0L
        tracker.down(x(from), y(from), time)
        val turns = mutableListOf<Int>()
        var angle = from
        while (angle != to) {
            angle += direction
            time += SLOW_MILLIS_PER_DEGREE
            tracker.move(x(angle), y(angle), time)?.let { turns += it.steps }
        }
        return turns
    }

    @Test
    fun `a clockwise third of a turn is five steps`() {
        assertEquals(5, slowTurn(from = 0, to = 130).sum())
    }

    @Test
    fun `a full turn is 16 steps (plus a degree to clear the boundary)`() {
        assertEquals(16, slowTurn(from = 0, to = 361).sum())
    }

    @Test
    fun `counter-clockwise turns are negative`() {
        assertEquals(-5, slowTurn(from = 130, to = 0).sum())
    }

    @Test
    fun `a move short of a step reports nothing`() {
        tracker.down(x(0), y(0), 0)
        assertNull(tracker.move(x(22), y(22), 100))
    }

    @Test
    fun `a move of a step reports one`() {
        tracker.down(x(0), y(0), 0)
        assertEquals(WheelEvent.Turn(1), tracker.move(x(25), y(25), 1_000))
    }

    @Test
    fun `the part of a step left over carries to the next move`() {
        tracker.down(x(0), y(0), 0)
        assertNull(tracker.move(x(15), y(15), 1_000))
        assertEquals(WheelEvent.Turn(1), tracker.move(x(30), y(30), 2_000))
        assertNull(tracker.move(x(40), y(40), 3_000))
        assertEquals(WheelEvent.Turn(1), tracker.move(x(50), y(50), 4_000))
    }

    @Test
    fun `reversing direction counts back from where the finger is`() {
        tracker.down(x(0), y(0), 0)
        assertEquals(WheelEvent.Turn(2), tracker.move(x(50), y(50), 1_000))
        assertEquals(WheelEvent.Turn(-1), tracker.move(x(20), y(20), 2_000))
    }

    @Test
    fun `clockwise across the seam keeps counting forward`() {
        // 345° to 15° is 30° clockwise through 0°, not 330° back.
        tracker.down(x(345), y(345), 0)
        assertEquals(WheelEvent.Turn(1), tracker.move(x(15), y(15), 1_000))
    }

    @Test
    fun `counter-clockwise across the seam keeps counting backward`() {
        tracker.down(x(15), y(15), 0)
        assertEquals(WheelEvent.Turn(-1), tracker.move(x(345), y(345), 1_000))
    }

    @Test
    fun `a finger at the center reports nothing`() {
        tracker.down(x(0), y(0), 0)
        assertNull(tracker.move(0f, 0f, 100))
    }

    @Test
    fun `a move before a down reports nothing`() {
        assertNull(tracker.move(x(90), y(90), 100))
    }

    @Test
    fun `a slow turn is not accelerated`() {
        val turns = slowTurn(from = 0, to = 190)
        assertTrue(turns.all { it == 1 })
        assertEquals(8, turns.sum())
    }

    @Test
    fun `a fast spin counts each step several times`() {
        // 144° in 96 ms is over four turns per second.
        tracker.down(x(0), y(0), 0)
        val steps = (1..FAST_FRAMES).sumOf { frame ->
            val angle = frame * FAST_DEGREES_PER_FRAME
            tracker.move(x(angle), y(angle), frame * FAST_FRAME_MILLIS)?.steps ?: 0
        }
        assertTrue("expected more than the 6 raw steps, got $steps", steps > 6)
    }

    @Test
    fun `a fast spin counterclockwise is accelerated the same way`() {
        tracker.down(x(0), y(0), 0)
        val steps = (1..FAST_FRAMES).sumOf { frame ->
            val angle = -frame * FAST_DEGREES_PER_FRAME
            tracker.move(x(angle), y(angle), frame * FAST_FRAME_MILLIS)?.steps ?: 0
        }
        assertTrue("expected fewer than -6, got $steps", steps < -6)
    }

    @Test
    fun `slowing down after a fast spin ends the acceleration`() {
        tracker.down(x(0), y(0), 0)
        var time = 0L
        var angle = 0
        repeat(FAST_FRAMES) {
            angle += FAST_DEGREES_PER_FRAME
            time += FAST_FRAME_MILLIS
            tracker.move(x(angle), y(angle), time)
        }
        // Then 25° very slowly: well after the speed window has emptied.
        time += 1_000
        angle += 25
        assertEquals(WheelEvent.Turn(1), tracker.move(x(angle), y(angle), time))
    }

    @Test
    fun `acceleration multiplier grows with speed`() {
        assertEquals(1, WheelAcceleration.multiplier(0.0))
        assertEquals(1, WheelAcceleration.multiplier(0.99))
        assertEquals(2, WheelAcceleration.multiplier(1.0))
        assertEquals(3, WheelAcceleration.multiplier(2.5))
        assertEquals(6, WheelAcceleration.multiplier(5.0))
    }

    @Test
    fun `a touch that never moves is a press`() {
        tracker.down(x(40), y(40), 0)
        assertTrue(tracker.up())
    }

    @Test
    fun `a touch that wobbles within half a step is a press`() {
        tracker.down(x(40), y(40), 0)
        tracker.move(x(44), y(44), 50)
        tracker.move(x(38), y(38), 100)
        assertTrue(tracker.up())
    }

    @Test
    fun `a touch that turns the ring by a step is not a press`() {
        tracker.down(x(0), y(0), 0)
        tracker.move(x(25), y(25), 1_000)
        assertFalse(tracker.up())
    }

    @Test
    fun `a drag short of a step is not a press`() {
        tracker.down(x(0), y(0), 0)
        tracker.move(x(15), y(15), 1_000)
        assertFalse(tracker.up())
    }

    @Test
    fun `a drag that comes back to where it started is still not a press`() {
        tracker.down(x(0), y(0), 0)
        tracker.move(x(40), y(40), 1_000)
        tracker.move(x(0), y(0), 2_000)
        assertFalse(tracker.up())
    }

    @Test
    fun `each gesture starts clean`() {
        tracker.down(x(0), y(0), 0)
        tracker.move(x(40), y(40), 1_000)
        assertFalse(tracker.up())

        tracker.down(x(40), y(40), 5_000)
        assertTrue(tracker.up())
    }

    @Test
    fun `a leftover fraction of a step does not carry into the next gesture`() {
        tracker.down(x(0), y(0), 0)
        tracker.move(x(15), y(15), 1_000)
        tracker.up()

        tracker.down(x(0), y(0), 5_000)
        assertNull(tracker.move(x(15), y(15), 6_000))
    }

    @Test
    fun `shortest delta crosses the seam both ways`() {
        assertEquals(20.0, shortestDelta(350.0, 10.0), EPSILON)
        assertEquals(-20.0, shortestDelta(10.0, 350.0), EPSILON)
        assertEquals(180.0, shortestDelta(0.0, 180.0), EPSILON)
    }

    @Test
    fun `angles run clockwise from three o'clock with y pointing down`() {
        assertEquals(0.0, angleOf(1f, 0f), EPSILON)
        assertEquals(90.0, angleOf(0f, 1f), EPSILON)
        assertEquals(-90.0, angleOf(0f, -1f), EPSILON)
    }

    @Test
    fun `a turn is never zero steps`() {
        val result = runCatching { WheelEvent.Turn(0) }
        assertTrue(result.isFailure)
    }

    private fun x(degrees: Int) = (RADIUS * cos(Math.toRadians(degrees.toDouble()))).toFloat()

    private fun y(degrees: Int) = (RADIUS * sin(Math.toRadians(degrees.toDouble()))).toFloat()

    private companion object {
        const val RADIUS = 100.0
        const val EPSILON = 1e-9
        const val SLOW_MILLIS_PER_DEGREE = 10L
        const val FAST_FRAMES = 6
        const val FAST_DEGREES_PER_FRAME = 24
        const val FAST_FRAME_MILLIS = 16L
    }
}
