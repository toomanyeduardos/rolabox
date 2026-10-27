package com.eduardoflores.rolabox.common.designsystem.wheel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WheelFeedbackTest {
    private var clicks = true
    private val vibrations = mutableListOf<WheelHaptic>()
    private val clicked = mutableListOf<WheelHaptic>()

    private fun TestScope.feedback() = WheelFeedback(
        scope = this,
        clicks = { clicks },
        vibrate = { vibrations += it },
        click = { clicked += it },
    )

    @Test
    fun aTurnOfNSteps_clicksNTimes() = runTest {
        feedback().perform(WheelEvent.Turn(5))
        advanceUntilIdle()

        assertEquals(List(5) { WheelHaptic.Tick }, clicked)
        assertEquals(List(5) { WheelHaptic.Tick }, vibrations)
    }

    @Test
    fun aTurnBackwards_clicksOncePerStep() = runTest {
        feedback().perform(WheelEvent.Turn(-3))
        advanceUntilIdle()

        assertEquals(List(3) { WheelHaptic.Tick }, clicked)
    }

    @Test
    fun aPress_clicksOnce() = runTest {
        val feedback = feedback()
        feedback.perform(WheelEvent.Center)
        feedback.perform(WheelEvent.Menu)
        advanceUntilIdle()

        assertEquals(listOf(WheelHaptic.Press, WheelHaptic.Press), clicked)
    }

    @Test
    fun aHold_clicksWhenItStartsAndNotWhenItIsReleased() = runTest {
        val feedback = feedback()
        feedback.perform(WheelEvent.HoldNext(HoldState.Held))
        feedback.perform(WheelEvent.HoldNext(HoldState.Released))
        advanceUntilIdle()

        assertEquals(listOf(WheelHaptic.Press), clicked)
    }

    @Test
    fun clickerOff_neverClicks_andStillVibrates() = runTest {
        clicks = false
        val feedback = feedback()
        feedback.perform(WheelEvent.Turn(4))
        feedback.perform(WheelEvent.Center)
        advanceUntilIdle()

        assertEquals(emptyList<WheelHaptic>(), clicked)
        assertEquals(4, vibrations.count { it == WheelHaptic.Tick })
        assertEquals(1, vibrations.count { it == WheelHaptic.Press })
    }

    @Test
    fun clickerTurnedOffDuringATurn_stopsClicking() = runTest {
        feedback().perform(WheelEvent.Turn(4))
        runCurrent()
        clicks = false
        advanceUntilIdle()

        assertEquals(listOf(WheelHaptic.Tick), clicked)
    }
}
