package com.eduardoflores.rolabox.core.auth.ui.google

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import com.eduardoflores.rolabox.core.auth.ui.SignInStep
import com.eduardoflores.rolabox.core.auth.ui.SignInStepResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Google's account picker, through Credential Manager. Everything about Google's SDK stays in this package. */
internal class GoogleSignInStep(private val context: Context, private val webClientId: String) : SignInStep {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun request(): SignInStepResult {
        // Every account on the device is offered, not only the ones that signed in to the app before:
        // Sign in and Create account share the button, and a new user has none.
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            credentialManager.getCredential(context, request).credential.asSignInStepResult()
        } catch (e: GetCredentialException) {
            e.asSignInStepResult()
        }
    }
}

internal fun GetCredentialException.asSignInStepResult(): SignInStepResult = when (this) {
    is GetCredentialCancellationException -> SignInStepResult.Cancelled
    is NoCredentialException -> SignInStepResult.NoAccount
    else -> SignInStepResult.Failed
}

private fun Credential.asSignInStepResult(): SignInStepResult =
    if (this is CustomCredential && type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        try {
            SignInStepResult.Credential(
                SignInCredential.GoogleIdToken(GoogleIdTokenCredential.createFrom(data).idToken),
            )
        } catch (_: GoogleIdTokenParsingException) {
            SignInStepResult.Failed
        }
    } else {
        SignInStepResult.Failed
    }
