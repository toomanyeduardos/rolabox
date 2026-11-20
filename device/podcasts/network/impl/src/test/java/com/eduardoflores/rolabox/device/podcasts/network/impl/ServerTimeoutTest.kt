package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// ADR-037, rule 4: offline, a delete is reported as Unavailable, not left queued as done.
class ServerTimeoutTest {
    @Test
    fun aWriteTheServerConfirmsInTime_succeeds() = runTest {
        assertEquals(Unit.right(), withinServerTimeout(TIMEOUT) { delay(TIMEOUT / 2) })
    }

    @Test
    fun aWriteTheServerNeverConfirms_isUnavailable() = runTest {
        assertEquals(RemoteError.Unavailable.left(), withinServerTimeout(TIMEOUT) { awaitCancellation() })
    }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
