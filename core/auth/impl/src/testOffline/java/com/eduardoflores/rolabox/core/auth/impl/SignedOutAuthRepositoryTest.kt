package com.eduardoflores.rolabox.core.auth.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignedOutAuthRepositoryTest {
    private val repository = SignedOutAuthRepository()

    @Test
    fun observeAuthState_isAlwaysSignedOut() = runTest {
        assertEquals(listOf(AuthState.SignedOut), repository.observeAuthState().toList())
    }

    @Test
    fun signIn_isUnavailable() = runTest {
        assertEquals(
            AuthError.Unavailable.left(),
            repository.signIn(SignInCredential.GoogleIdToken("token")),
        )
    }

    @Test
    fun signUp_isUnavailable() = runTest {
        assertEquals(AuthError.Unavailable.left(), repository.signUp("Alex", "alex@mail.com", "password12"))
    }

    @Test
    fun signOut_succeeds() = runTest {
        assertEquals(Unit.right(), repository.signOut())
    }
}
