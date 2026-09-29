package com.eduardoflores.rolabox.core.auth.testing

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// The fake stands in for the real repository in other modules' tests, so it has to behave like one.
class FakeAuthRepositoryTest {
    private val repository = FakeAuthRepository()
    private val credential = SignInCredential.GoogleIdToken("token")

    @Test
    fun signIn_success_signsIn() = runTest {
        assertEquals(repository.signInUser.right(), repository.signIn(credential))
        assertEquals(AuthState.SignedIn(repository.signInUser), repository.observeAuthState().first())
        assertEquals(credential, repository.lastSignInCredential)
    }

    @Test
    fun signIn_error_leavesStateUnchanged() = runTest {
        repository.signInError = AuthError.Network

        assertEquals(AuthError.Network.left(), repository.signIn(credential))
        assertEquals(AuthState.SignedOut, repository.observeAuthState().first())
    }

    @Test
    fun signUp_success_signsInWithTheName() = runTest {
        val user = repository.signUp("Alex", "alex@mail.com", "password12").getOrNull()

        assertEquals("Alex", user?.displayName)
        assertEquals(AuthState.SignedIn(user!!), repository.observeAuthState().first())
        assertEquals(SignUpRequest("Alex", "alex@mail.com", "password12"), repository.lastSignUp)
    }

    @Test
    fun signUp_error_leavesStateUnchanged() = runTest {
        repository.signUpError = AuthError.EmailAlreadyInUse

        assertEquals(AuthError.EmailAlreadyInUse.left(), repository.signUp("Alex", "alex@mail.com", "password12"))
        assertEquals(AuthState.SignedOut, repository.observeAuthState().first())
    }

    @Test
    fun signOut_signsOut() = runTest {
        repository.signIn(credential)

        assertEquals(Unit.right(), repository.signOut())
        assertEquals(AuthState.SignedOut, repository.observeAuthState().first())
    }

    @Test
    fun signOut_error_leavesStateUnchanged() = runTest {
        repository.signIn(credential)
        repository.signOutError = AuthError.Network

        assertEquals(AuthError.Network.left(), repository.signOut())
        assertEquals(AuthState.SignedIn(repository.signInUser), repository.observeAuthState().first())
    }
}
