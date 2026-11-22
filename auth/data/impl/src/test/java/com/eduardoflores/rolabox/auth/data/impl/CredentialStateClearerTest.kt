package com.eduardoflores.rolabox.auth.data.impl

import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.ClearCredentialInterruptedException
import androidx.credentials.exceptions.ClearCredentialProviderConfigurationException
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.credentials.exceptions.ClearCredentialUnsupportedException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// ADR-009, rule 6: the session's end clears Credential Manager's saved state. ADR-007: only Credential Manager's own
// exceptions become an error. ADR-030, rule 6: only the ones that mean something is broken are reported.
class CredentialStateClearerTest {
    private val credentialManager = FakeCredentialManager()
    private val reports = FakeNonFatalReport()
    private val clearer = CredentialManagerStateClearer(credentialManager, reports)

    @Test
    fun clearing_succeeds() = runTest {
        assertEquals(Unit.right(), clearer.clear())

        assertEquals(1, credentialManager.clears)
        assertEquals(emptyList<String>(), reports.kinds)
    }

    @Test
    fun aBrokenOrMissingProvider_isAnError_andIsReported() = runTest {
        val failures = listOf(
            ClearCredentialUnknownException() to "ClearCredentialUnknownException",
            ClearCredentialProviderConfigurationException() to "ClearCredentialProviderConfigurationException",
            ClearCredentialUnsupportedException() to "ClearCredentialUnsupportedException",
        )
        for ((failure, name) in failures) {
            credentialManager.failure = failure

            assertEquals(AuthError.CredentialStateNotCleared.left(), clearer.clear())
            assertEquals("AuthError.CredentialStateNotCleared $name", reports.kinds.last())
        }
        assertEquals(failures.size, reports.kinds.size)
    }

    // Retrying works, so it's an expected outcome, not something broken.
    @Test
    fun beingInterrupted_isAnError_andIsNotReported() = runTest {
        credentialManager.failure = ClearCredentialInterruptedException()

        assertEquals(AuthError.CredentialStateNotCleared.left(), clearer.clear())
        assertEquals(emptyList<String>(), reports.kinds)
    }

    @Test
    fun anythingElse_isABug_andIsRethrown() {
        credentialManager.bug = IllegalStateException("bug")

        assertThrows(IllegalStateException::class.java) { runTest { clearer.clear() } }
        assertEquals(emptyList<String>(), reports.kinds)
    }

    /** Answers only the clear, at once, with [failure] or [bug] when one is set. */
    private class FakeCredentialManager : CredentialManager {
        var failure: ClearCredentialException? = null
        var bug: RuntimeException? = null
        var clears = 0
            private set

        // Credential Manager's own signature answers the clear with a Void.
        @Suppress("ForbiddenVoid")
        override fun clearCredentialStateAsync(
            request: ClearCredentialStateRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<Void?, ClearCredentialException>,
        ) {
            clears++
            bug?.let { throw it }
            val error = failure
            if (error == null) callback.onResult(null) else callback.onError(error)
        }

        override fun getCredentialAsync(
            context: Context,
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
        ) = throw UnsupportedOperationException()

        override fun getCredentialAsync(
            context: Context,
            pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
        ) = throw UnsupportedOperationException()

        override fun prepareGetCredentialAsync(
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
        ) = throw UnsupportedOperationException()

        override fun createCredentialAsync(
            context: Context,
            request: CreateCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
        ) = throw UnsupportedOperationException()

        override fun createSettingsPendingIntent(): PendingIntent = throw UnsupportedOperationException()
    }
}
