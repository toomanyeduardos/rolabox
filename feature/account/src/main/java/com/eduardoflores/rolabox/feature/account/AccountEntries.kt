package com.eduardoflores.rolabox.feature.account

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Where the account flow starts. */
@Serializable
data object SignInKey : NavKey

@Serializable
data object CreateAccountKey : NavKey

/** Reset password. [email] is what the user had typed on Sign in, and may be empty. */
@Serializable
data class ResetPasswordKey(val email: String = "") : NavKey

/**
 * The entries of the account flow (ADR-012). The screens report their exits as lambdas, and `:app`
 * decides where each one goes.
 */
@Suppress("LongParameterList")
fun EntryProviderScope<NavKey>.accountEntries(
    onCreateAccountClick: () -> Unit,
    onForgotPasswordClick: (email: String) -> Unit,
    onSignInClick: () -> Unit,
    onBack: () -> Unit,
    onBackToSignIn: () -> Unit,
    onSignedUp: () -> Unit,
    onSignedIn: () -> Unit,
) {
    entry<SignInKey> {
        SignInRoute(
            onCreateAccountClick = onCreateAccountClick,
            onForgotPasswordClick = onForgotPasswordClick,
            onSignedIn = onSignedIn,
        )
    }
    entry<CreateAccountKey> {
        CreateAccountRoute(
            onBack = onBack,
            onSignInClick = onSignInClick,
            onSignedUp = onSignedUp,
        )
    }
    entry<ResetPasswordKey> { key ->
        ResetPasswordRoute(
            email = key.email,
            onBackClick = onBack,
            onSignInClick = onBackToSignIn,
        )
    }
}
