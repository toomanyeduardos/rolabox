package com.eduardoflores.rolabox.feature.account

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Where the account flow starts. A placeholder until the Sign in screen exists. */
@Serializable
data object SignInKey : NavKey

@Serializable
data object CreateAccountKey : NavKey

/**
 * The entries of the account flow (ADR-012). The screens report their exits as lambdas, and `:app`
 * decides where each one goes.
 */
fun EntryProviderScope<NavKey>.accountEntries(
    onCreateAccountClick: () -> Unit,
    onSignInClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onBack: () -> Unit,
    onSignedUp: () -> Unit,
) {
    entry<SignInKey> {
        SignInPlaceholderScreen(onCreateAccountClick = onCreateAccountClick)
    }
    entry<CreateAccountKey> {
        CreateAccountRoute(
            onBack = onBack,
            onSignInClick = onSignInClick,
            onGoogleClick = onGoogleClick,
            onSignedUp = onSignedUp,
        )
    }
}
