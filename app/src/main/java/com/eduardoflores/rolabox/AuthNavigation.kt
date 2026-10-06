package com.eduardoflores.rolabox

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.auth.ui.api.AuthUiEntries
import com.eduardoflores.rolabox.auth.ui.api.CreateAccountKey
import com.eduardoflores.rolabox.auth.ui.api.ResetPasswordKey

/** Adds the auth screens, and maps each of their exits to a change of the app stack (ADR-012). */
internal fun EntryProviderScope<NavKey>.authEntries(entries: AuthUiEntries, navigator: AppNavigator) {
    entries.appStackEntries(
        scope = this,
        onCreateAccountClick = { navigator.push(CreateAccountKey) },
        onForgotPasswordClick = { email -> navigator.push(ResetPasswordKey(email)) },
        // Sign in is below Create account in the stack, so going back is going to it.
        onSignInClick = navigator::pop,
        onBack = navigator::pop,
        // Sign in is the auth stack's first screen, so it's below Reset password whichever way it was reached.
        onBackToSignIn = navigator::popToRoot,
        onSignedUp = navigator::accessGranted,
        onSignedIn = navigator::accessGranted,
    )
}
