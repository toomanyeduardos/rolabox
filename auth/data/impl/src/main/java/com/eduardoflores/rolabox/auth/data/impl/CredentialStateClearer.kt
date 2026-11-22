package com.eduardoflores.rolabox.auth.data.impl

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.ClearCredentialInterruptedException
import arrow.core.Either
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.common.util.catchNamed
import javax.inject.Inject

private const val TAG = "CredentialStateClearer"

/**
 * Clears what Credential Manager keeps about the last sign-in, when the session ends: on sign-out and once the account
 * is deleted (ADR-009, rule 6). It's an interface so that the delete-account use case's tests can check when it runs.
 */
internal fun interface CredentialStateClearer {
    /** Fails with [AuthError.CredentialStateNotCleared]. */
    suspend fun clear(): Either<AuthError, Unit>
}

/** Credential Manager's own clear, which needs only the application context (ADR-009, rule 6). */
internal class CredentialManagerStateClearer @Inject constructor(
    private val credentialManager: CredentialManager,
    private val nonFatalReport: NonFatalReport,
) : CredentialStateClearer {
    override suspend fun clear(): Either<AuthError, Unit> = catchNamed(::asAuthError) {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    // Only Credential Manager's own exceptions are named (ADR-007, rule 4). Being interrupted is expected and retrying
    // works, so it isn't reported; every other kind means the provider is missing or broken (ADR-030, rule 6). The
    // class name is the kind: a custom exception's type is the provider's own string.
    private fun asAuthError(throwable: Throwable): AuthError? {
        if (throwable !is ClearCredentialException) return null
        val kind = "AuthError.CredentialStateNotCleared ${throwable.javaClass.simpleName}"
        Log.w(TAG, kind)
        if (throwable !is ClearCredentialInterruptedException) nonFatalReport.report(kind, throwable)
        return AuthError.CredentialStateNotCleared
    }
}
