package com.eduardoflores.rolabox.auth.data.impl

import arrow.core.Either
import arrow.core.left
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.auth.data.testing.SignInWithEmailRequest
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {
    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()
    private val signIn = DefaultSignInUseCase(authRepository, userDataRepository)
    private val credential = SignInCredential.GoogleIdToken("id-token")

    private suspend fun offlineModeChosen() = userDataRepository.observeOfflineModeChosen().first().getOrNull()

    @Test
    fun aCredential_isExchangedForASession() = runTest {
        val result = signIn(credential)

        assertEquals(authRepository.signInUser, result.getOrNull())
        assertEquals(credential, authRepository.lastSignInCredential)
    }

    @Test
    fun anEmailAndPassword_signIn() = runTest {
        val result = signIn("toomanyeduardos@gmail.com", "secret")

        assertEquals(authRepository.signInUser, result.getOrNull())
        assertEquals(SignInWithEmailRequest("toomanyeduardos@gmail.com", "secret"), authRepository.lastSignInWithEmail)
    }

    @Test
    fun fromOfflineMode_offlineModeIsOffBeforeTheFirebaseRequest() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        var offlineDuringRequest: Boolean? = null
        val recording = object : AuthRepository by authRepository {
            override suspend fun signIn(credential: SignInCredential): Either<AuthError, AuthUser> {
                offlineDuringRequest = offlineModeChosen()
                return authRepository.signIn(credential)
            }
        }

        DefaultSignInUseCase(recording, userDataRepository)(credential)

        assertEquals(false, offlineDuringRequest)
        assertEquals(false, offlineModeChosen())
        assertTrue(authRepository.observeAuthState().first() is AuthState.SignedIn)
    }

    @Test
    fun fromOfflineMode_aFailedSignInTurnsOfflineModeBackOn() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        authRepository.signInError = AuthError.Network

        val result = signIn(credential)

        assertEquals(SignInError.Auth(AuthError.Network).left(), result)
        assertEquals(true, offlineModeChosen())
    }

    @Test
    fun fromOfflineMode_aFailedEmailSignInTurnsOfflineModeBackOn() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        authRepository.signInWithEmailError = AuthError.InvalidCredential

        val result = signIn("toomanyeduardos@gmail.com", "secret")

        assertEquals(SignInError.Auth(AuthError.InvalidCredential).left(), result)
        assertEquals(true, offlineModeChosen())
    }

    @Test
    fun notOffline_aFailedSignInDoesNotChooseOfflineMode() = runTest {
        authRepository.signInError = AuthError.Network

        signIn(credential)

        assertEquals(false, offlineModeChosen())
    }

    @Test
    fun notOffline_nothingIsWritten() = runTest {
        // A write would fail, so a success shows that none was made.
        userDataRepository.writeError = StorageError.Unavailable

        val result = signIn(credential)

        assertEquals(authRepository.signInUser, result.getOrNull())
    }

    @Test
    fun whenOfflineModeCantBeTurnedOff_noFirebaseRequestIsMade() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.writeError = StorageError.Unavailable

        val result = signIn(credential)

        assertEquals(SignInError.Storage(StorageError.Unavailable).left(), result)
        assertNull(authRepository.lastSignInCredential)
        assertEquals(true, offlineModeChosen())
    }

    @Test
    fun whenTheChoiceCantBeRead_offlineModeIsTurnedOffAnyway() = runTest {
        userDataRepository.setReadError(StorageError.Corrupted)
        userDataRepository.writeError = StorageError.Unavailable

        val result = signIn(credential)

        // The write was attempted, and its failure stops the sign-in before the Firebase request.
        assertEquals(SignInError.Storage(StorageError.Unavailable).left(), result)
        assertNull(authRepository.lastSignInCredential)
    }
}
