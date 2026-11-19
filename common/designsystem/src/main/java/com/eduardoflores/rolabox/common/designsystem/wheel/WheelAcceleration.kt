package com.eduardoflores.rolabox.common.designsystem.wheel

/**
 * How many times each step counts at a given spin speed, so a long list can be crossed with one
 * flick and no screen does that math (ADR-018). Slow turning is exact: one step is one row.
 *
 * Speed is in full turns per second, so the tiers stay where they are if the step size changes.
 */
internal object WheelAcceleration {
    /** Speed, in turns per second, from which each multiplier applies. */
    private val tiers = listOf(
        TIER_FAST_TURNS_PER_SECOND to MULTIPLIER_FAST,
        TIER_FASTER_TURNS_PER_SECOND to MULTIPLIER_FASTER,
        TIER_FASTEST_TURNS_PER_SECOND to MULTIPLIER_FASTEST,
    )

    fun multiplier(turnsPerSecond: Double): Int = tiers.lastOrNull { (from, _) -> turnsPerSecond >= from }?.second ?: 1

    /**
     * The fewest steps of a turn from the faster tier on, where the rows go by too fast to be read. A
     * list tells a fast turn from its steps alone, so the wheel's vocabulary doesn't change (ADR-018, rule 4).
     */
    const val FAST_TURN_STEPS = 3

    private const val TIER_FAST_TURNS_PER_SECOND = 1.0
    private const val TIER_FASTER_TURNS_PER_SECOND = 2.0
    private const val TIER_FASTEST_TURNS_PER_SECOND = 3.0
    private const val MULTIPLIER_FAST = 2
    private const val MULTIPLIER_FASTER = FAST_TURN_STEPS
    private const val MULTIPLIER_FASTEST = 6
}
