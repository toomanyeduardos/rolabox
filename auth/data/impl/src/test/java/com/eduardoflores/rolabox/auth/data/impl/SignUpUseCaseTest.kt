package com.eduardoflores.rolabox.auth.data.impl

import arrow.core.left
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.auth.data.testing.SignUpRequest
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignUpUseCaseTest {
    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()
    private val signUp = DefaultSignUpUseCase(authRepository, userDataRepository)

    private suspend fun offlineModeChosen() = userDataRepository.observeOfflineModeChosen().first().getOrNull()

    @Test
    fun createsTheAccount() = runTest {
        val result = signUp("Eduardo", "toomanyeduardos@gmail.com", "Secret1!")

        assertEquals("Eduardo", result.getOrNull()?.displayName)
        assertEquals(SignUpRequest("Eduardo", "toomanyeduardos@gmail.com", "Secret1!"), authRepository.lastSignUp)
    }

    @Test
    fun fromOfflineMode_leavesOfflineMode() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        signUp("Eduardo", "toomanyeduardos@gmail.com", "Secret1!")

        assertEquals(false, offlineModeChosen())
    }

    @Test
    fun fromOfflineMode_aFailedSignUpTurnsOfflineModeBackOn() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        authRepository.signUpError = AuthError.EmailAlreadyInUse

        val result = signUp("Eduardo", "toomanyeduardos@gmail.com", "Secret1!")

        assertEquals(SignInError.Auth(AuthError.EmailAlreadyInUse).left(), result)
        assertEquals(true, offlineModeChosen())
    }

    @Test
    fun whenOfflineModeCantBeTurnedOff_noAccountIsCreated() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.writeError = StorageError.Unavailable

        val result = signUp("Eduardo", "toomanyeduardos@gmail.com", "Secret1!")

        assertEquals(SignInError.Storage(StorageError.Unavailable).left(), result)
        assertNull(authRepository.lastSignUp)
    }
}
