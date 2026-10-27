package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the highlighted row's text starts and stops scrolling, on the test's virtual clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class RowTextScrollTest {
    /** A frame every 16 ms of virtual time, stamped with that time. */
    private class FakeFrameClock(private val scope: TestScope) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MILLIS)
            return onFrame(scope.testScheduler.currentTime * NANOS_PER_MILLI)
        }
    }

    /** The offsets reported, with the virtual time each was reported at. */
    private val reports = mutableListOf<Pair<Long, Float>>()

    private fun TestScope.startScrolling(overflow: Float) = launch(FakeFrameClock(this)) {
        scrollRowText(overflow, PX_PER_SECOND) { reports += testScheduler.currentTime to it }
    }

    @Test
    fun `a text that fits never moves`() = runTest {
        val job = startScrolling(overflow = 0f)
        advanceTimeBy(CYCLE_MILLIS * 2)
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(emptyList<Pair<Long, Float>>(), reports)
    }

    @Test
    fun `the text rests at its start before it scrolls`() = runTest {
        val job = startScrolling(OVERFLOW)
        advanceTimeBy(ROW_SCROLL_START_WAIT_MILLIS)
        assertEquals(emptyList<Pair<Long, Float>>(), reports)
        job.cancel()
    }

    @Test
    fun `after the wait it moves toward its end`() = runTest {
        val job = startScrolling(OVERFLOW)
        advanceTimeBy(ROW_SCROLL_START_WAIT_MILLIS + SCROLL_MILLIS / 2)
        val (_, halfway) = reports.last()
        assertTrue("moved: $halfway", halfway > 0f && halfway < OVERFLOW)
        assertTrue(reports.zipWithNext().all { (a, b) -> b.second >= a.second })
        job.cancel()
    }

    @Test
    fun `it stops at its end, rests, and starts again from its start`() = runTest {
        val job = startScrolling(OVERFLOW)
        val reachedEnd = ROW_SCROLL_START_WAIT_MILLIS + SCROLL_MILLIS + FRAME_MILLIS
        advanceTimeBy(reachedEnd + 1)
        val (endTime, end) = reports.last()
        assertEquals(OVERFLOW, end)

        advanceTimeBy(ROW_SCROLL_END_WAIT_MILLIS - FRAME_MILLIS)
        assertEquals("rests at its end", OVERFLOW, reports.last().second)

        advanceTimeBy(FRAME_MILLIS * 2)
        val (backTime, back) = reports.last()
        assertEquals(0f, back)
        assertEquals(endTime + ROW_SCROLL_END_WAIT_MILLIS, backTime)

        val reportsAtStart = reports.size
        advanceTimeBy(ROW_SCROLL_START_WAIT_MILLIS)
        assertEquals("rests at its start again", reportsAtStart, reports.size)
        advanceTimeBy(FRAME_MILLIS * 3)
        assertTrue("moves again", reports.last().second > 0f)
        job.cancel()
    }

    @Test
    fun `moving the highlight stops it at once`() = runTest {
        val job = startScrolling(OVERFLOW)
        advanceTimeBy(ROW_SCROLL_START_WAIT_MILLIS + SCROLL_MILLIS / 2)
        job.cancel()
        val reportsAtCancel = reports.size
        advanceTimeBy(CYCLE_MILLIS * 2)
        assertEquals(reportsAtCancel, reports.size)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
        const val PX_PER_SECOND = 100f
        const val OVERFLOW = 200f
        const val SCROLL_MILLIS = 2_000L
        const val CYCLE_MILLIS = ROW_SCROLL_START_WAIT_MILLIS + SCROLL_MILLIS + ROW_SCROLL_END_WAIT_MILLIS
    }
}
