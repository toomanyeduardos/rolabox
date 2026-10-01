package com.eduardoflores.rolabox.core.designsystem.wheel

import kotlin.math.abs
import kotlin.math.atan2

/** One step of the wheel, in degrees: 24 steps make a full turn. */
internal const val STEP_DEGREES = 15.0

private const val HALF_TURN_DEGREES = 180.0
private const val FULL_TURN_DEGREES = 360.0
private const val MILLIS_PER_SECOND = 1000.0

/** How far the finger may move on the ring and still be a press: half a step. */
internal const val PRESS_SLOP_DEGREES = STEP_DEGREES / 2

/** Speed is measured over this long, so the jitter of a single touch sample doesn't speed up a turn. */
private const val SPEED_WINDOW_MILLIS = 100L

/**
 * Turns a finger moving around the wheel's center into [WheelEvent.Turn]s, and tells a press from
 * a drag. It is plain logic: positions are relative to the center, in pixels with y pointing down,
 * and time is passed in, so it needs no Android and is tested on the JVM.
 *
 * Feed it one gesture at a time: [down], any number of [move]s, then [up].
 */
internal class WheelTurnTracker {
    private var lastAngle = 0.0
    private var pendingDegrees = 0.0
    private var totalDegrees = 0.0
    private var travelledDegrees = 0.0
    private var tracking = false
    private var turned = false
    private val samples = ArrayDeque<Sample>()

    /** Whether the gesture in progress has moved far enough around the ring to no longer be a press. */
    val hasTurned: Boolean get() = turned

    /** The finger touched the wheel at ([x], [y]). */
    fun down(x: Float, y: Float, timeMillis: Long) {
        tracking = true
        turned = false
        pendingDegrees = 0.0
        totalDegrees = 0.0
        travelledDegrees = 0.0
        lastAngle = angleOf(x, y)
        samples.clear()
        samples.addLast(Sample(timeMillis, 0.0))
    }

    /**
     * The finger moved to ([x], [y]). Returns the turn it completes, or null while it hasn't
     * covered a whole step since the last one. A fraction of a step is kept for the next move.
     */
    fun move(x: Float, y: Float, timeMillis: Long): WheelEvent.Turn? {
        // The center has no angle, and a finger there has not moved around it.
        val onRing = tracking && !(x == 0f && y == 0f)
        return if (onRing) turnBy(angleOf(x, y), timeMillis) else null
    }

    /** The finger lifted. Returns true when it was a press: it never turned the ring. */
    fun up(): Boolean {
        val press = tracking && !turned
        tracking = false
        samples.clear()
        return press
    }

    private fun turnBy(angle: Double, timeMillis: Long): WheelEvent.Turn? {
        val delta = shortestDelta(lastAngle, angle)
        lastAngle = angle

        totalDegrees += delta
        if (abs(totalDegrees) > PRESS_SLOP_DEGREES) turned = true
        travelledDegrees += abs(delta)
        pendingDegrees += delta

        val multiplier = WheelAcceleration.multiplier(speedStepsPerSecond(timeMillis))
        val steps = (pendingDegrees / STEP_DEGREES).toInt()
        pendingDegrees -= steps * STEP_DEGREES
        return if (steps == 0) null else WheelEvent.Turn(steps * multiplier)
    }

    private fun speedStepsPerSecond(timeMillis: Long): Double {
        samples.addLast(Sample(timeMillis, travelledDegrees))
        while (samples.size > 2 && timeMillis - samples[1].timeMillis >= SPEED_WINDOW_MILLIS) {
            samples.removeFirst()
        }
        val first = samples.first()
        val elapsed = timeMillis - first.timeMillis
        if (elapsed <= 0) return 0.0
        val degrees = travelledDegrees - first.travelledDegrees
        return degrees / STEP_DEGREES / (elapsed / MILLIS_PER_SECOND)
    }

    private class Sample(val timeMillis: Long, val travelledDegrees: Double)
}

/** Clockwise angle of a point around the center, in degrees. With y pointing down, atan2 is clockwise. */
internal fun angleOf(x: Float, y: Float): Double = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))

/** The signed angle from [from] to [to] by the short way round, in (-180, 180]: it crosses the 0°/360° seam. */
internal fun shortestDelta(from: Double, to: Double): Double {
    val delta = (to - from) % FULL_TURN_DEGREES
    return when {
        delta > HALF_TURN_DEGREES -> delta - FULL_TURN_DEGREES
        delta <= -HALF_TURN_DEGREES -> delta + FULL_TURN_DEGREES
        else -> delta
    }
}
