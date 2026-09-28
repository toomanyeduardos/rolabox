package com.eduardoflores.rolabox.splash

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashHandoffTest {
    @Test
    fun systemSplashIconAngle_atTheStartAndEnd_isTheRestingAngle() {
        assertEquals(0f, systemSplashIconAngle(elapsedMillis = 0, durationMillis = 1_000), DELTA)
        assertEquals(0f, systemSplashIconAngle(elapsedMillis = 1_000, durationMillis = 1_000), DELTA)
    }

    @Test
    fun systemSplashIconAngle_atTheMiddle_isHalfATurn() {
        assertEquals(180f, systemSplashIconAngle(elapsedMillis = 500, durationMillis = 1_000), DELTA)
    }

    @Test
    fun systemSplashIconAngle_speedsUpThenSlowsDown() {
        // accelerate_decelerate: a quarter of the time covers less than a quarter of the turn.
        assertEquals(52.7f, systemSplashIconAngle(elapsedMillis = 250, durationMillis = 1_000), 0.1f)
    }

    @Test
    fun systemSplashIconAngle_outsideTheAnimation_isClamped() {
        assertEquals(0f, systemSplashIconAngle(elapsedMillis = -50, durationMillis = 1_000), DELTA)
        assertEquals(0f, systemSplashIconAngle(elapsedMillis = 5_000, durationMillis = 1_000), DELTA)
    }

    @Test
    fun systemSplashIconAngle_withoutADuration_isTheRestingAngle() {
        assertEquals(0f, systemSplashIconAngle(elapsedMillis = 300, durationMillis = 0), DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
