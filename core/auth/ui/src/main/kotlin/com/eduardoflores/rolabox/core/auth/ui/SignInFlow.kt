package com.eduardoflores.rolabox.core.auth.ui

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository

/** Why signing in with a provider failed, in the terms the screens show. Cancelling isn't one of them. */
enum class SignInError { Network, TooManyRequests, AccountDisabled, NoAccount, Unknown }

sealed interface SignInOutcome {
    data object SignedIn : SignInOutcome

    /** The user closed the account picker. Nothing is shown. */
    data object Cancelled : SignInOutcome

    data class Failed(val error: SignInError) : SignInOutcome
}

/**
 * The part of signing in with a provider that isn't UI, shared by every screen that offers it: the
 * data step of ADR-009 and the outcomes of both steps. It works the same for every provider, since
 * the step's result carries the `:api`'s `SignInCredential`.
 *
 * It only passes the credential to [AuthRepository.signIn], and [prepare] only turns offline mode
 * off. Neither combines repositories into one result, so it isn't a use case (ADR-001).
 *
 * Signing in creates the account if there isn't one, so the screens end the same way. An email that
 * already has a password account is linked to it by Firebase (ADR-013), so there is nothing to
 * handle here.
 */
class SignInFlow(private val authRepository: AuthRepository, private val userDataRepository: UserDataRepository) {
    /**
     * A provider's step reaches the network, so offline mode is turned off before it starts (ADR-009,
     * ADR-008 rule 7). Returns the error to show if that can't be saved, and null to go ahead.
     */
    suspend fun prepare(): SignInError? = userDataRepository.setOfflineModeChosen(false).fold(
        ifLeft = { SignInError.Unknown },
        ifRight = { null },
    )

    suspend fun complete(result: SignInStepResult): SignInOutcome = when (result) {
        SignInStepResult.Cancelled -> SignInOutcome.Cancelled

        SignInStepResult.NoAccount -> SignInOutcome.Failed(SignInError.NoAccount)

        SignInStepResult.Failed -> SignInOutcome.Failed(SignInError.Unknown)

        is SignInStepResult.Credential -> authRepository.signIn(result.credential).fold(
            ifLeft = { SignInOutcome.Failed(it.asSignInError()) },
            ifRight = { SignInOutcome.SignedIn },
        )
    }
}

private fun AuthError.asSignInError(): SignInError = when (this) {
    AuthError.Network -> SignInError.Network

    AuthError.TooManyRequests -> SignInError.TooManyRequests

    AuthError.AccountDisabled -> SignInError.AccountDisabled

    // Signing in with a credential can't be an email or password error, and an expired one asks to start over.
    AuthError.InvalidCredential,
    AuthError.EmailAlreadyInUse,
    AuthError.InvalidEmail,
    AuthError.WeakPassword,
    AuthError.Unknown,
    -> SignInError.Unknown
}
