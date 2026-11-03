package com.eduardoflores.rolabox.device.library.network.impl

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps requests at least [interval] apart, for the whole app: MusicBrainz asks for about one a second (ADR-027,
 * rule 2). A request waits for the one before it.
 */
internal class RequestSpacing(private val interval: Duration, private val timeSource: TimeSource.WithComparableMarks) {
    private val mutex = Mutex()
    private var last: ComparableTimeMark? = null

    suspend fun <T> next(request: suspend () -> T): T = mutex.withLock {
        last?.let { delay(interval - it.elapsedNow()) }
        try {
            request()
        } finally {
            last = timeSource.markNow()
        }
    }
}
