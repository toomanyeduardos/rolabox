package com.eduardoflores.rolabox.auth.ui.impl.signin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.google.GoogleSignInStep

/**
 * A provider's account picker is due. A screen's ViewModel asks for it in its UI state, with the
 * configuration it injected, since this package injects nothing (ADR-014, rule 5).
 */
internal data class SignInRequest(val provider: SignInProvider, val config: SignInConfig)

/**
 * What the UI step of a sign-in provider produced (ADR-009). Only [Credential] goes on, to the
 * sign-in use case. The rest are outcomes of the step itself, which are not `AuthError`s.
 */
internal sealed interface SignInStepResult {
    data class Credential(val credential: SignInCredential) : SignInStepResult

    /** The user closed the account picker. That's a choice, not an error. */
    data object Cancelled : SignInStepResult

    /** The provider has no account on the device to pick. */
    data object NoAccount : SignInStepResult

    /** The provider's SDK failed for another reason, or returned something that isn't a credential. */
    data object Failed : SignInStepResult
}

/** Shows a provider's account picker, which needs an `Activity` (ADR-009). One per provider. */
internal fun interface SignInStep {
    suspend fun request(): SignInStepResult
}

private fun signInStep(provider: SignInProvider, context: Context, config: SignInConfig): SignInStep = when (provider) {
    SignInProvider.Google -> GoogleSignInStep(context, config.googleWebClientId)
}

/**
 * Shows the account picker of [request]'s provider while it is set, and reports the result once.
 * Every screen that signs in uses it, so none of them runs a provider's SDK itself. Setting
 * [request] again after a result starts another one.
 */
@Composable
internal fun SignInStepEffect(request: SignInRequest?, onResult: (SignInStepResult) -> Unit) {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(request) {
        if (request != null) currentOnResult(signInStep(request.provider, context, request.config).request())
    }
}
