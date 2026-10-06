package com.eduardoflores.rolabox.auth.data.impl

import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseAuthErrorsTest {
    @Test
    fun emailCollision_isEmailAlreadyInUse() {
        val error = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "in use")

        assertEquals(AuthError.EmailAlreadyInUse, error.asAuthError())
    }

    // A weak-password exception is a kind of invalid-credentials exception, so it must not be mistaken for one.
    @Test
    fun weakPassword_isWeakPassword() {
        val error = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short")

        assertEquals(AuthError.WeakPassword, error.asAuthError())
    }

    @Test
    fun invalidEmail_isInvalidEmail() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "bad email")

        assertEquals(AuthError.InvalidEmail, error.asAuthError())
    }

    @Test
    fun otherInvalidCredentials_areInvalidCredential() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "expired")

        assertEquals(AuthError.InvalidCredential, error.asAuthError())
    }

    @Test
    fun disabledUser_isAccountDisabled() {
        val error = FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled")

        assertEquals(AuthError.AccountDisabled, error.asAuthError())
    }

    @Test
    fun network_isNetwork() {
        assertEquals(AuthError.Network, FirebaseNetworkException("offline").asAuthError())
    }

    @Test
    fun tooManyRequests_isTooManyRequests() {
        assertEquals(AuthError.TooManyRequests, FirebaseTooManyRequestsException("slow down").asAuthError())
    }

    @Test
    fun unnamedFirebaseAuthError_isUnknown() {
        assertEquals(AuthError.Unknown, FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "off").asAuthError())
    }

    @Test
    fun anythingElse_isNotNamed() {
        assertNull(IllegalStateException("bug").asAuthError())
    }
}
