package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountError
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountUseCase
import com.eduardoflores.rolabox.auth.data.api.SignInMethod
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The user re-authenticates with what their account has, which also proves the phone is online before anything is
 * deleted (ADR-037). Leaving the screen without deleting lifts the pause a failed deletion keeps on sync. A deletion
 * that succeeds has Android erase the app's data, which closes the app, so the exit to Sign in rarely shows.
 */
@HiltViewModel
internal class DeleteAccountViewModelImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val deleteAccount: DeleteAccountUseCase,
    private val signInConfig: SignInConfig,
) : DeleteAccountViewModel() {
    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    override val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val methods = (authRepository.observeAuthState().first() as? AuthState.SignedIn)?.user?.signInMethods
            // An account whose methods aren't known is offered both, so the user is never left with no way to confirm.
            val known = methods.orEmpty().ifEmpty { SignInMethod.entries.toSet() }
            _uiState.update {
                it.copy(showsPassword = SignInMethod.PASSWORD in known, showsGoogle = SignInMethod.GOOGLE in known)
            }
        }
    }

    override fun onPasswordChange(password: String) = _uiState.update {
        it.copy(password = password, passwordError = null, formError = null)
    }

    override fun onDeleteClick() {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return
        // The password is left as typed, since spaces can be part of it.
        if (current.password.isEmpty()) {
            _uiState.update { it.copy(passwordError = DeleteAccountPasswordError.Required, formError = null) }
            return
        }

        _uiState.update { it.copy(isLoading = true, passwordError = null, formError = null) }
        viewModelScope.launch {
            deleteAccount(current.password).applyTo(withPassword = true)
        }
    }

    override fun onProviderClick(provider: SignInProvider) {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return

        _uiState.update {
            it.copy(
                isLoading = true,
                passwordError = null,
                formError = null,
                signInRequest = SignInRequest(provider, signInConfig),
            )
        }
    }

    override fun onSignInResult(result: SignInStepResult) {
        if (_uiState.value.signInRequest == null) return

        _uiState.update { it.copy(signInRequest = null) }
        when (result) {
            SignInStepResult.Cancelled -> _uiState.update { it.copy(isLoading = false) }

            SignInStepResult.NoAccount -> _uiState.update { it.withFormError(DeleteAccountFormError.NoAccount) }

            SignInStepResult.Failed -> _uiState.update { it.withFormError(DeleteAccountFormError.Unknown) }

            is SignInStepResult.Credential -> viewModelScope.launch {
                deleteAccount(result.credential).applyTo(withPassword = false)
            }
        }
    }

    // Leaving without deleting, by Cancel, back or the system's back, resumes sync. After a deletion, there's
    // nothing paused, and resuming does nothing.
    override fun onCleared() {
        deleteAccount.cancel()
    }

    private fun Either<DeleteAccountError, Unit>.applyTo(withPassword: Boolean) = fold(
        ifLeft = { error -> _uiState.update { it.withError(error, withPassword) } },
        ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isFinished = true) } },
    )
}

private fun DeleteAccountUiState.withFormError(error: DeleteAccountFormError) =
    copy(isLoading = false, formError = error)

private fun DeleteAccountUiState.withError(error: DeleteAccountError, withPassword: Boolean): DeleteAccountUiState =
    when (error) {
        is DeleteAccountError.Remote -> when (error.cause) {
            RemoteError.Unavailable -> withFormError(DeleteAccountFormError.Network)

            // The rules refused, or the backend isn't set up: a bug, and nothing the user can fix.
            RemoteError.Rejected -> withFormError(DeleteAccountFormError.Unknown)
        }

        is DeleteAccountError.Auth -> when (error.cause) {
            // With a password, it's the password. With Google, the picker gave another account: Firebase's user
            // mismatch, which is InvalidCredential too.
            AuthError.InvalidCredential ->
                if (withPassword) {
                    copy(isLoading = false, passwordError = DeleteAccountPasswordError.Wrong)
                } else {
                    withFormError(DeleteAccountFormError.OtherAccount)
                }

            AuthError.Network -> withFormError(DeleteAccountFormError.Network)

            AuthError.RequiresRecentLogin -> withFormError(DeleteAccountFormError.SignInAgain)

            // Rate limiting, a disabled account, or errors that can't come from re-authenticating and deleting.
            AuthError.TooManyRequests,
            AuthError.AccountDisabled,
            AuthError.EmailAlreadyInUse,
            AuthError.InvalidEmail,
            AuthError.WeakPassword,
            AuthError.Unknown,
            -> withFormError(DeleteAccountFormError.Unknown)
        }
    }
