package com.eduardoflores.rolabox.auth.ui.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.auth.ui.api.AuthUiEntries
import com.eduardoflores.rolabox.auth.ui.api.CreateAccountKey
import com.eduardoflores.rolabox.auth.ui.api.ResetPasswordKey
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.auth.ui.api.SignOutKey
import javax.inject.Inject

/**
 * The entries of the auth screens. This is the one place that names their concrete ViewModels, since
 * Hilt creates a ViewModel by its class. The routes take the abstract ones (ADR-021).
 */
internal class DefaultAuthUiEntries @Inject constructor() : AuthUiEntries {
    override fun appStackEntries(
        scope: EntryProviderScope<NavKey>,
        onCreateAccountClick: () -> Unit,
        onForgotPasswordClick: (email: String) -> Unit,
        onSignInClick: () -> Unit,
        onBack: () -> Unit,
        onBackToSignIn: () -> Unit,
        onSignedUp: () -> Unit,
        onSignedIn: () -> Unit,
        onSignedOut: () -> Unit,
    ) = with(scope) {
        entry<SignInKey> {
            SignInRoute(
                viewModel = hiltViewModel<SignInViewModelImpl>(),
                onCreateAccountClick = onCreateAccountClick,
                onForgotPasswordClick = onForgotPasswordClick,
                onSignedIn = onSignedIn,
            )
        }
        entry<CreateAccountKey> {
            CreateAccountRoute(
                viewModel = hiltViewModel<CreateAccountViewModelImpl>(),
                onBack = onBack,
                onSignInClick = onSignInClick,
                onSignedUp = onSignedUp,
            )
        }
        entry<ResetPasswordKey> { key ->
            ResetPasswordRoute(
                viewModel = hiltViewModel<ResetPasswordViewModelImpl>(),
                email = key.email,
                onBackClick = onBack,
                onSignInClick = onBackToSignIn,
            )
        }
        entry<SignOutKey> {
            SignOutRoute(
                viewModel = hiltViewModel<SignOutViewModelImpl>(),
                onCancelClick = onBack,
                onSignedOut = onSignedOut,
            )
        }
    }
}
