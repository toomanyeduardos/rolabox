package com.eduardoflores.rolabox.auth.data.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountError
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountUseCase
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records what it was asked, and answers what the test set. It holds none of the real use case's order (ADR-037),
 * which is tested where it is implemented, in `:auth:data:impl`.
 */
@Singleton
class FakeDeleteAccountUseCase @Inject constructor() : DeleteAccountUseCase {
    /** When set, deleting fails with this error. */
    var error: DeleteAccountError? = null

    /** The credential passed to the last deletion with one. */
    var lastCredential: SignInCredential? = null
        private set

    /** The password passed to the last deletion with one. */
    var lastPassword: String? = null
        private set

    /** How many times [cancel] was called. */
    var cancels = 0
        private set

    override suspend operator fun invoke(credential: SignInCredential): Either<DeleteAccountError, Unit> {
        lastCredential = credential
        return error?.left() ?: Unit.right()
    }

    override suspend operator fun invoke(password: String): Either<DeleteAccountError, Unit> {
        lastPassword = password
        return error?.left() ?: Unit.right()
    }

    override fun cancel() {
        cancels++
    }
}
