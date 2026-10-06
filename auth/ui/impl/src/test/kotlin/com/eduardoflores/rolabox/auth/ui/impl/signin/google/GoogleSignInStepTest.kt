package com.eduardoflores.rolabox.auth.ui.impl.signin.google

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInStepTest {
    @Test
    fun closingThePicker_isCancelled() {
        assertEquals(SignInStepResult.Cancelled, GetCredentialCancellationException().asSignInStepResult())
    }

    @Test
    fun noGoogleAccount_isNoAccount() {
        assertEquals(SignInStepResult.NoAccount, NoCredentialException().asSignInStepResult())
    }

    @Test
    fun anythingElse_isFailed() {
        assertEquals(SignInStepResult.Failed, GetCredentialUnknownException().asSignInStepResult())
    }
}
