package com.eduardoflores.rolabox.common.userdata.network.impl

import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreErrorsTest {
    // A project with no Firestore database answers NOT_FOUND. It used to be rethrown, so WorkManager
    // failed every sync with nothing in the logs but its own FAILURE line.
    @Test
    fun noDatabase_isRejected() {
        assertEquals(RemoteError.Rejected, firestoreException(Code.NOT_FOUND).asRemoteError())
    }

    @Test
    fun rulesOrInvalidRequest_isRejected() {
        listOf(Code.PERMISSION_DENIED, Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION).forEach {
            assertEquals(it.name, RemoteError.Rejected, firestoreException(it).asRemoteError())
        }
    }

    @Test
    fun transientFailure_isUnavailable() {
        listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.RESOURCE_EXHAUSTED, Code.UNAUTHENTICATED)
            .forEach { assertEquals(it.name, RemoteError.Unavailable, firestoreException(it).asRemoteError()) }
    }

    // ADR-007: anything not named is a bug, and is rethrown.
    @Test
    fun otherCodesAndExceptions_areNotConverted() {
        assertNull(firestoreException(Code.INTERNAL).asRemoteError())
        assertNull(IllegalStateException().asRemoteError())
    }

    // ADR-030: a rejected request is a bug or a setup mistake, and is reported with its code.
    @Test
    fun rejected_isReportedWithItsCode() {
        val report = FakeNonFatalReport()

        firestoreException(Code.PERMISSION_DENIED).asLoggedRemoteError(report)

        assertEquals(listOf("RemoteError.Rejected PERMISSION_DENIED"), report.kinds)
    }

    // Every offline device gets these, on every retry.
    @Test
    fun unavailable_isNotReported() {
        val report = FakeNonFatalReport()

        firestoreException(Code.UNAVAILABLE).asLoggedRemoteError(report)

        assertEquals(emptyList<String>(), report.kinds)
    }

    private fun firestoreException(code: Code) = FirebaseFirestoreException("test", code)
}
