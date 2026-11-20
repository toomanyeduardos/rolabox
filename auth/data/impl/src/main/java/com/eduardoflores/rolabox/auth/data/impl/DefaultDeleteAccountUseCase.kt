package com.eduardoflores.rolabox.auth.data.impl

import android.util.Log
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountError
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountUseCase
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val TAG = "DeleteAccount"

/** The order of ADR-037: why each step comes where it does is said in Decision. The last step closes the app. */
internal class DefaultDeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val appDataEraser: AppDataEraser,
) : DeleteAccountUseCase {
    override suspend operator fun invoke(credential: SignInCredential): Either<DeleteAccountError, Unit> =
        deleteAfter { authRepository.reauthenticate(credential) }

    override suspend operator fun invoke(password: String): Either<DeleteAccountError, Unit> =
        deleteAfter { authRepository.reauthenticateWithPassword(password) }

    override fun cancel() = syncRepository.resume()

    private suspend fun deleteAfter(
        reauthenticate: suspend () -> Either<AuthError, Unit>,
    ): Either<DeleteAccountError, Unit> = either {
        // Read before anything changes: deleting the account signs out. The screen is only open while signed in, so
        // nobody being signed in is an answer we can't explain to the user.
        val userId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)?.user?.id
            ?: raise(DeleteAccountError.Auth(AuthError.Unknown))
        // Offline, or with the wrong credential, this fails, and nothing is deleted.
        reauthenticate().mapLeft(DeleteAccountError::Auth).bind()
        // The data goes first: once the account is gone, the rules refuse it. Sync stays paused from here on,
        // whatever happens, until the end or cancel().
        syncRepository.deleteRemoteData(userId).mapLeft(DeleteAccountError::Remote).bind()
        authRepository.deleteUser().mapLeft(DeleteAccountError::Auth).bind()
        // The phone goes back to a fresh install: Android erases everything and closes the app. Nothing after this
        // is sure to run. The account is gone whatever happens here, so Android refusing doesn't fail the deletion
        // (ADR-007, rule 7). It's logged (ADR-029), sync resumes for nobody, as the user is signed out, and the user
        // goes to Sign in with the phone's data still there.
        if (!appDataEraser.erase()) {
            Log.w(TAG, "The account was deleted, but Android refused to erase the app's data")
            syncRepository.resume()
        }
    }
}
