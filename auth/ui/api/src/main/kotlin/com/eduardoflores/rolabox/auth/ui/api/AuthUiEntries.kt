package com.eduardoflores.rolabox.auth.ui.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of the auth screens (ADR-020): it adds their entries, and takes every way out
 * of them as a lambda. `:app` injects it, and decides where each exit goes (ADR-012).
 */
interface AuthUiEntries {
    /** Adds the entries of [SignInKey], [CreateAccountKey] and [ResetPasswordKey], full screens on the app stack. */
    @Suppress("LongParameterList") // One lambda per exit, so the contract lists how its screens are left.
    fun appStackEntries(
        scope: EntryProviderScope<NavKey>,
        onCreateAccountClick: () -> Unit,
        onForgotPasswordClick: (email: String) -> Unit,
        onSignInClick: () -> Unit,
        onBack: () -> Unit,
        onBackToSignIn: () -> Unit,
        onSignedUp: () -> Unit,
        onSignedIn: () -> Unit,
    )
}
