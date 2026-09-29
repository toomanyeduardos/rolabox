package com.eduardoflores.rolabox.core.domain

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import kotlinx.coroutines.flow.first

/**
 * Runs [signIn], the first Firebase request of a sign-in, with offline mode off, and turns offline
 * mode back on if the sign-in fails and the user had chosen it (ADR-008, rule 7). Every sign-in use
 * case goes through here, so the rule holds for every way of signing in.
 */
internal suspend fun UserDataRepository.leavingOfflineModeFor(
    signIn: suspend () -> Either<AuthError, AuthUser>,
): Either<SignInError, AuthUser> = either {
    // Null when the choice can't be read. Offline mode is then turned off anyway, since no Firebase
    // request may be made while it could be chosen, but it isn't turned back on after a failure.
    val wasOffline = observeOfflineModeChosen().first().getOrNull()
    if (wasOffline != false) setOfflineModeChosen(false).mapLeft(SignInError::Storage).bind()

    signIn()
        .onLeft {
            // If this fails, the user is left signed out and not offline, so the app opens on Sign in
            // next time, where they can choose offline mode again. The sign-in error is what they see.
            if (wasOffline == true) setOfflineModeChosen(true)
        }
        .mapLeft(SignInError::Auth)
        .bind()
}
