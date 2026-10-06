package com.eduardoflores.rolabox.auth.data.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignUpUseCase
import javax.inject.Inject
import javax.inject.Singleton

/** Records what it was asked, and answers what the test set. The real rule is tested in `:auth:data:impl`. */
@Singleton
class FakeSignUpUseCase @Inject constructor() : SignUpUseCase {
    /** When set, signing up fails with this error. */
    var error: SignInError? = null

    /** The name, email and password passed to the last call. */
    var lastRequest: SignUpRequest? = null
        private set

    /** Succeeds with a user that has the given [name]. */
    override suspend operator fun invoke(name: String, email: String, password: String): Either<SignInError, AuthUser> {
        lastRequest = SignUpRequest(name, email, password)
        return error?.left() ?: AuthUser(id = "fake-user", displayName = name, photoUrl = null).right()
    }
}
