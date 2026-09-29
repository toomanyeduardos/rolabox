package com.eduardoflores.rolabox.core.auth.ui

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignInFlowTest {
    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()
    private val flow = SignInFlow(authRepository, userDataRepository)
    private val credential = SignInCredential.GoogleIdToken("id-token")

    @Test
    fun prepare_turnsOfflineModeOff() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        assertNull(flow.prepare())

        assertEquals(false, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun prepare_whenOfflineModeCantBeSaved_returnsAnError() = runTest {
        userDataRepository.writeError = StorageError.Unavailable

        assertEquals(SignInError.Unknown, flow.prepare())
    }

    @Test
    fun aCredential_isExchangedForASession() = runTest {
        val outcome = flow.complete(SignInStepResult.Credential(credential))

        assertEquals(SignInOutcome.SignedIn, outcome)
        assertEquals(credential, authRepository.lastSignInCredential)
    }

    @Test
    fun cancelling_isNotAnErrorAndDoesNotCallTheBackend() = runTest {
        val outcome = flow.complete(SignInStepResult.Cancelled)

        assertEquals(SignInOutcome.Cancelled, outcome)
        assertNull(authRepository.lastSignInCredential)
    }

    @Test
    fun noAccount_andAFailedStep_areFailuresWithoutCallingTheBackend() = runTest {
        assertEquals(SignInOutcome.Failed(SignInError.NoAccount), flow.complete(SignInStepResult.NoAccount))
        assertEquals(SignInOutcome.Failed(SignInError.Unknown), flow.complete(SignInStepResult.Failed))
        assertNull(authRepository.lastSignInCredential)
    }

    @Test
    fun backendErrors_areFailures() = runTest {
        mapOf(
            AuthError.Network to SignInError.Network,
            AuthError.TooManyRequests to SignInError.TooManyRequests,
            AuthError.AccountDisabled to SignInError.AccountDisabled,
            AuthError.InvalidCredential to SignInError.Unknown,
            AuthError.Unknown to SignInError.Unknown,
        ).forEach { (error, signInError) ->
            authRepository.signInError = error

            val outcome = flow.complete(SignInStepResult.Credential(credential))

            assertEquals("$error", SignInOutcome.Failed(signInError), outcome)
        }
    }
}
