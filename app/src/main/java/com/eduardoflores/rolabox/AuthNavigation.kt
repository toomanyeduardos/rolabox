package com.eduardoflores.rolabox

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.feature.account.CreateAccountKey
import com.eduardoflores.rolabox.feature.account.accountEntries

internal fun EntryProviderScope<NavKey>.authEntries(navigator: AppNavigator) {
    accountEntries(
        onCreateAccountClick = { navigator.push(CreateAccountKey) },
        // Reset password comes with its own ticket, which will push its key with the email.
        onForgotPasswordClick = {},
        // Sign in is below Create account in the stack, so going back is going to it.
        onSignInClick = navigator::pop,
        onBack = navigator::pop,
        onSignedUp = navigator::leaveAuth,
        onSignedIn = navigator::leaveAuth,
    )
}
