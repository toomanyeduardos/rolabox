package com.eduardoflores.rolabox.common.crashreporting.impl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** ADR-030 rule 5: a report holds its kind and the cause's stack trace, and none of the cause's text. */
class ReportedErrorTest {
    private val cause = IllegalStateException(
        "users/uid-123/settings/preferences",
        java.io.IOException("/storage/emulated/0/Music/Some Artist/Some Song.mp3"),
    )

    @Test
    fun `the message is the kind`() {
        assertEquals(
            "RemoteError.Rejected PERMISSION_DENIED",
            ReportedError.of("RemoteError.Rejected PERMISSION_DENIED", cause).message,
        )
    }

    @Test
    fun `the stack trace is the cause's`() {
        assertArrayEquals(cause.stackTrace, ReportedError.of("kind", cause).stackTrace)
    }

    @Test
    fun `the cause isn't attached`() {
        assertNull(ReportedError.of("kind", cause).cause)
    }

    @Test
    fun `no text of the cause or its causes is in the report`() {
        val printed = ReportedError.of("kind", cause).stackTraceToString()
        assertFalse(printed.contains("uid-123"))
        assertFalse(printed.contains("Some Song"))
    }

    @Test
    fun `without a cause, the stack trace is the caller's`() {
        val frames = ReportedError.of("kind", null).stackTrace.map { it.methodName }
        assertEquals(true, frames.any { it.contains("without a cause") })
    }
}
