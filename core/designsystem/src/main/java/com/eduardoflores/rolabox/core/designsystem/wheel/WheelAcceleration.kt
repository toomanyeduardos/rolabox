package com.eduardoflores.rolabox.core.designsystem.wheel

/**
 * How many times each step counts at a given spin speed, so a long list can be crossed with one
 * flick and no screen does that math (ADR-018). Slow turning is exact: one step is one row.
 */
internal object WheelAcceleration {
    /** Speed, in steps per second, from which each multiplier applies. A full turn is 24 steps. */
    private val tiers = listOf(
        TIER_FAST_STEPS_PER_SECOND to MULTIPLIER_FAST,
        TIER_FASTER_STEPS_PER_SECOND to MULTIPLIER_FASTER,
        TIER_FASTEST_STEPS_PER_SECOND to MULTIPLIER_FASTEST,
    )

    fun multiplier(stepsPerSecond: Double): Int = tiers.lastOrNull { (from, _) -> stepsPerSecond >= from }?.second ?: 1

    private const val TIER_FAST_STEPS_PER_SECOND = 12.0
    private const val TIER_FASTER_STEPS_PER_SECOND = 24.0
    private const val TIER_FASTEST_STEPS_PER_SECOND = 48.0
    private const val MULTIPLIER_FAST = 2
    private const val MULTIPLIER_FASTER = 4
    private const val MULTIPLIER_FASTEST = 8
}
