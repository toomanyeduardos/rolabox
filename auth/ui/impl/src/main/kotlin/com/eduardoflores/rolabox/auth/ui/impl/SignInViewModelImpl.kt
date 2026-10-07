package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignInUseCase
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class SignInViewModelImpl @Inject constructor(
    private val signIn: SignInUseCase,
    private val userDataRepository: UserDataRepository,
    private val signInConfig: SignInConfig,
) : SignInViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    override val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    // Editing a field clears what was said about it. A bad login is about both fields, so editing
    // either one clears it, and so does the LCD error that goes with it.
    override fun onEmailChange(email: String) = _uiState.update {
        it.copy(email = email, emailError = null, passwordError = it.passwordError.unlessBadLogin(), formError = null)
    }

    override fun onPasswordChange(password: String) = _uiState.update {
        it.copy(password = password, passwordError = null, formError = null)
    }

    override fun onSubmit() {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return

        // The password is left as typed, since spaces can be part of it. The email is trimmed, since
        // keyboards and autofill often add a trailing space.
        val email = current.email.trim()
        val emailError = validateEmail(email)
        val passwordError = if (current.password.isEmpty()) SignInPasswordError.Required else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, formError = null) }
            return
        }

        _uiState.update { it.copy(isLoading = true, emailError = null, passwordError = null, formError = null) }
        viewModelScope.launch {
            signIn(email, current.password).fold(
                ifLeft = { error -> _uiState.update { it.withEmailSignInError(error) } },
                ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isFinished = true) } },
            )
        }
    }

    override fun onProviderClick(provider: SignInProvider) {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return

        _uiState.update {
            it.copy(
                isLoading = true,
                emailError = null,
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

            SignInStepResult.NoAccount -> _uiState.update { it.withFormError(SignInFormError.NoAccount) }

            SignInStepResult.Failed -> _uiState.update { it.withFormError(SignInFormError.Unknown) }

            is SignInStepResult.Credential -> viewModelScope.launch {
                signIn(result.credential).fold(
                    ifLeft = { error -> _uiState.update { it.withProviderError(error) } },
                    ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isFinished = true) } },
                )
            }
        }
    }

    override fun onOfflineClick() {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return

        _uiState.update { it.copy(isLoading = true, emailError = null, passwordError = null, formError = null) }
        viewModelScope.launch {
            userDataRepository.setOfflineModeChosen(true).fold(
                ifLeft = { _ -> _uiState.update { it.copy(isLoading = false, formError = SignInFormError.Unknown) } },
                ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isFinished = true) } },
            )
        }
    }
}

private fun SignInPasswordError?.unlessBadLogin(): SignInPasswordError? =
    if (this == SignInPasswordError.BadLogin) null else this

private fun SignInUiState.withFormError(error: SignInFormError): SignInUiState =
    copy(isLoading = false, formError = error)

private fun SignInUiState.withEmailSignInError(error: SignInError): SignInUiState = when (error) {
    is SignInError.Auth -> withAuthError(error.cause)

    // Offline mode couldn't be turned off, so the sign-in didn't start. There's nothing the user can fix.
    is SignInError.Storage -> withFormError(SignInFormError.Unknown)
}

private fun SignInUiState.withAuthError(error: AuthError): SignInUiState = when (error) {
    // The email format is checked before the call, so the backend calling it invalid is an
    // edge case. It still gets the generic answer, not a hint about the account.
    AuthError.InvalidCredential,
    AuthError.InvalidEmail,
    -> copy(isLoading = false, passwordError = SignInPasswordError.BadLogin)

    AuthError.Network -> copy(isLoading = false, formError = SignInFormError.Network)

    AuthError.TooManyRequests -> copy(isLoading = false, formError = SignInFormError.TooManyRequests)

    AuthError.AccountDisabled -> copy(isLoading = false, formError = SignInFormError.AccountDisabled)

    // Sign-up errors can't come from signing in, so if one does, it's one we can't tell the user about.
    AuthError.Unknown,
    AuthError.EmailAlreadyInUse,
    AuthError.WeakPassword,
    -> copy(isLoading = false, formError = SignInFormError.Unknown)
}

// A provider's credential isn't an email or password, so an invalid or expired one asks to start over.
private fun SignInUiState.withProviderError(error: SignInError): SignInUiState = withFormError(
    when (error) {
        is SignInError.Storage -> SignInFormError.Unknown

        is SignInError.Auth -> when (error.cause) {
            AuthError.Network -> SignInFormError.Network

            AuthError.TooManyRequests -> SignInFormError.TooManyRequests

            AuthError.AccountDisabled -> SignInFormError.AccountDisabled

            AuthError.InvalidCredential,
            AuthError.EmailAlreadyInUse,
            AuthError.InvalidEmail,
            AuthError.WeakPassword,
            AuthError.Unknown,
            -> SignInFormError.Unknown
        }
    },
)
