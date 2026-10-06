package com.eduardoflores.rolabox.auth.data.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignInUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records what it was asked, and answers what the test set. It holds none of the real use case's
 * rule: leaving offline mode (ADR-008, rule 7) is tested where it is implemented, in `:auth:data:impl`.
 */
@Singleton
class FakeSignInUseCase @Inject constructor() : SignInUseCase {
    /** The user a successful sign-in returns. */
    var user = AuthUser(id = "fake-user", displayName = "Fake User", photoUrl = null)

    /** When set, signing in with a credential fails with this error. */
    var credentialError: SignInError? = null

    /** When set, signing in with an email and password fails with this error. */
    var emailError: SignInError? = null

    /** The credential passed to the last sign-in with one. */
    var lastCredential: SignInCredential? = null
        private set

    /** The email and password passed to the last sign-in with them. */
    var lastEmailRequest: SignInWithEmailRequest? = null
        private set

    override suspend operator fun invoke(credential: SignInCredential): Either<SignInError, AuthUser> {
        lastCredential = credential
        return credentialError?.left() ?: user.right()
    }

    override suspend operator fun invoke(email: String, password: String): Either<SignInError, AuthUser> {
        lastEmailRequest = SignInWithEmailRequest(email, password)
        return emailError?.left() ?: user.right()
    }
}
