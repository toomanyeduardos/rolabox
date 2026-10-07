package com.eduardoflores.rolabox

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

// The wheel's gestures, as a finger makes them (ADR-018), for the tests that drive the device with it.

internal fun SemanticsNodeInteraction.pressCenter() {
    performTouchInput { click(center) }
}

internal fun SemanticsNodeInteraction.pressMenu() {
    performTouchInput { click(pointOnRing(MENU_ANGLE_DEGREES)) }
}

/**
 * Turns the ring by [steps] steps, clockwise when positive: a finger that goes round it slowly enough
 * that the wheel applies no acceleration, so a step moves a row.
 */
internal fun SemanticsNodeInteraction.turn(steps: Int) {
    performTouchInput {
        val direction = sign(steps.toFloat())
        val moves = abs(steps) * MOVES_PER_STEP + 1
        down(pointOnRing(START_ANGLE_DEGREES))
        repeat(moves) { move ->
            advanceEventTime(MOVE_INTERVAL_MILLIS)
            moveTo(pointOnRing(START_ANGLE_DEGREES + direction * (move + 1) * DEGREES_PER_MOVE))
        }
        up()
    }
}

// Degrees clockwise from 3 o'clock, as the wheel measures them: MENU is at the top.
private const val MENU_ANGLE_DEGREES = -90f
private const val START_ANGLE_DEGREES = 45f
private const val DEGREES_PER_MOVE = 5f
private const val MOVES_PER_STEP = 3
private const val MOVE_INTERVAL_MILLIS = 100L
private const val RING_POSITION = 0.75f

/** A point on the ring at [degrees], relative to the wheel being touched. */
private fun TouchInjectionScope.pointOnRing(degrees: Float): Offset {
    val radius = width / 2f * RING_POSITION
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}
