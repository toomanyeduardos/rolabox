package com.eduardoflores.rolabox.common.designsystem.wheel

import kotlin.math.abs
import kotlin.math.hypot

// The diagonals that divide the ring into four quarters.
private const val SECTOR_START = 45.0
private const val SECTOR_END = 135.0

/** The four buttons on the wheel's ring. The center button is not one of them: it has no ring position. */
internal enum class WheelButton(val holdable: Boolean) {
    Menu(holdable = true),
    Previous(holdable = true),
    Next(holdable = true),
    PlayPause(holdable = false),
    ;

    fun pressed(): WheelEvent = when (this) {
        Menu -> WheelEvent.Menu
        Previous -> WheelEvent.Previous
        Next -> WheelEvent.Next
        PlayPause -> WheelEvent.PlayPause
    }

    fun held(state: HoldState): WheelEvent? = when (this) {
        Menu -> if (state == HoldState.Held) WheelEvent.HoldMenu else null
        Previous -> WheelEvent.HoldPrevious(state)
        Next -> WheelEvent.HoldNext(state)
        PlayPause -> null
    }

    companion object {
        /** The button at [angle] (degrees clockwise from 3 o'clock): each owns the quarter around its side. */
        fun at(angle: Double): WheelButton = when {
            angle >= -SECTOR_END && angle < -SECTOR_START -> Menu
            angle >= -SECTOR_START && angle < SECTOR_START -> Next
            angle >= SECTOR_START && angle < SECTOR_END -> PlayPause
            else -> Previous
        }
    }
}

/**
 * Turns one finger's touch of the wheel into [WheelEvent]s: a turn around the ring, a press of the
 * center or of a ring button, and the hold of MENU, ⏮ and ⏭. It is plain logic, with positions
 * relative to the wheel's center in pixels and time passed in, so it is tested on the JVM; the
 * composable only feeds it pointer input and a timer.
 *
 * A touch that starts on the ring is a press of the button under it until it moves past the press
 * slop, and a turn after that. A touch that starts on the center is a press only. One that starts
 * outside the ring is ignored.
 *
 * Feed it one gesture at a time: [down], [move]s, then [up], or [cancel]. The owner of the timer
 * calls [holdTimeout] when [canHold] is true and the press has lasted long enough.
 */
internal class WheelGestureRecognizer(private val centerRadius: Float, private val ringRadius: Float) {
    private val tracker = WheelTurnTracker()
    private var target: Target = Target.None
    private var held = false
    private var lastX = 0f
    private var lastY = 0f

    /** Whether a hold can still start: the finger is on a holdable button, hasn't turned, and isn't holding. */
    val canHold: Boolean
        get() = (target as? Target.Ring)?.button?.holdable == true && !held && !tracker.hasTurned

    fun down(x: Float, y: Float, timeMillis: Long) {
        held = false
        lastX = x
        lastY = y
        val distance = hypot(x, y)
        target = when {
            distance > ringRadius -> Target.None
            distance <= centerRadius -> Target.Center
            else -> Target.Ring(WheelButton.at(angleOf(x, y)))
        }
        if (target is Target.Ring) tracker.down(x, y, timeMillis)
    }

    /** The finger moved. Returns the turn it completes, if any. A held button ignores movement. */
    fun move(x: Float, y: Float, timeMillis: Long): WheelEvent.Turn? {
        lastX = x
        lastY = y
        return if (target is Target.Ring && !held) tracker.move(x, y, timeMillis) else null
    }

    /** The hold time has passed. Returns the hold's start, or null when [canHold] is false. */
    fun holdTimeout(): WheelEvent? {
        val button = (target as? Target.Ring)?.button
        return if (canHold && button != null) {
            held = true
            button.held(HoldState.Held)
        } else {
            null
        }
    }

    /** The finger lifted. Returns the press it completes, or the release of a hold. */
    fun up(): WheelEvent? {
        val event = when (val current = target) {
            Target.None -> null

            Target.Center -> if (hypot(lastX, lastY) <= centerRadius) WheelEvent.Center else null

            is Target.Ring -> {
                val press = tracker.up()
                if (held) {
                    current.button.held(HoldState.Released)
                } else if (press) {
                    current.button.pressed()
                } else {
                    null
                }
            }
        }
        reset()
        return event
    }

    /** The gesture was interrupted. A hold in progress is released, so it never lasts forever. */
    fun cancel(): WheelEvent? {
        val release = (target as? Target.Ring)?.takeIf { held }?.button?.held(HoldState.Released)
        tracker.up()
        reset()
        return release
    }

    private fun reset() {
        target = Target.None
        held = false
    }

    private sealed interface Target {
        data object None : Target
        data object Center : Target
        data class Ring(val button: WheelButton) : Target
    }
}

/** How strong a vibration an event gets. */
internal enum class WheelHaptic { Tick, Press }

/** How many ticks an event gets: one per step of a turn, so a fast flick buzzes several times. */
internal fun WheelEvent.tickCount(): Int = if (this is WheelEvent.Turn) abs(steps) else 0

/** A light tick for a turn, a stronger one when a button is pressed or a hold begins, none on a release. */
internal fun WheelEvent.haptic(): WheelHaptic? = when (this) {
    is WheelEvent.Turn -> WheelHaptic.Tick
    is WheelEvent.HoldPrevious -> if (state == HoldState.Held) WheelHaptic.Press else null
    is WheelEvent.HoldNext -> if (state == HoldState.Held) WheelHaptic.Press else null
    else -> WheelHaptic.Press
}
