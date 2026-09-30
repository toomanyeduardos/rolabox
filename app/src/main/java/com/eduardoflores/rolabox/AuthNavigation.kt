package com.eduardoflores.rolabox

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.feature.account.CreateAccountKey
import com.eduardoflores.rolabox.feature.account.ResetPasswordKey
import com.eduardoflores.rolabox.feature.account.accountEntries

internal fun EntryProviderScope<NavKey>.authEntries(navigator: AppNavigator) {
    accountEntries(
        onCreateAccountClick = { navigator.push(CreateAccountKey) },
        onForgotPasswordClick = { email -> navigator.push(ResetPasswordKey(email)) },
        // Sign in is below Create account in the stack, so going back is going to it.
        onSignInClick = navigator::pop,
        onBack = navigator::pop,
        // Sign in is the auth stack's first screen, so it's below Reset password whichever way it was reached.
        onBackToSignIn = navigator::popToRoot,
        onSignedUp = navigator::leaveAuth,
        onSignedIn = navigator::leaveAuth,
    )
}
