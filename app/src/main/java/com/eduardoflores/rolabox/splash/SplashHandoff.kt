package com.eduardoflores.rolabox.splash

import kotlin.math.PI
import kotlin.math.cos

/**
 * Where the system splash left off when it handed over to [RolaboxSplash], so the Compose record
 * carries on from the same angle instead of jumping back to the start.
 */
internal data class SplashHandoff(val iconAngle: Float)

private const val FULL_TURN = 360f

/**
 * The angle of the record in the system splash [elapsedMillis] after its animation started. It
 * mirrors `avd_vinyl_spin.xml`: one turn over [durationMillis] with the accelerate_decelerate
 * interpolator. Times outside the animation give the angle it starts or ends at (the same one).
 */
internal fun systemSplashIconAngle(elapsedMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0) return 0f
    val fraction = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    val eased = (cos((fraction + 1) * PI) / 2 + 0.5).toFloat()
    return eased * FULL_TURN % FULL_TURN
}
