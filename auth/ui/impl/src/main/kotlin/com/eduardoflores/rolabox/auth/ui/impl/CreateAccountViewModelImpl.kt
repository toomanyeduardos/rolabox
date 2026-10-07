package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.PasswordPolicy
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignInUseCase
import com.eduardoflores.rolabox.auth.data.api.SignUpUseCase
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class CreateAccountViewModelImpl @Inject constructor(
    private val signUp: SignUpUseCase,
    private val signIn: SignInUseCase,
    private val passwordPolicy: PasswordPolicy,
    private val signInConfig: SignInConfig,
) : CreateAccountViewModel() {
    private val _uiState = MutableStateFlow(CreateAccountUiState())
    override val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    // Editing a field clears what was said about it, since that error no longer describes the text.
    override fun onNameChange(name: String) =
        _uiState.update { it.copy(name = name, nameError = null, formError = null) }

    override fun onEmailChange(email: String) = _uiState.update {
        it.copy(email = email, emailError = null, formError = null)
    }

    override fun onPasswordChange(password: String) = _uiState.update {
        it.copy(
            password = password,
            strength = passwordPolicy.strengthOf(password),
            passwordError = null,
            formError = null,
        )
    }

    override fun onProviderClick(provider: SignInProvider) {
        val current = _uiState.value
        if (current.isLoading || current.isSignedUp) return

        _uiState.update {
            it.copy(isLoading = true, formError = null, signInRequest = SignInRequest(provider, signInConfig))
        }
    }

    override fun onSignInResult(result: SignInStepResult) {
        if (_uiState.value.signInRequest == null) return

        _uiState.update { it.copy(signInRequest = null) }
        when (result) {
            SignInStepResult.Cancelled -> _uiState.update { it.copy(isLoading = false) }

            SignInStepResult.NoAccount -> _uiState.update { it.withFormError(FormError.NoAccount) }

            SignInStepResult.Failed -> _uiState.update { it.withFormError(FormError.SignInFailed) }

            is SignInStepResult.Credential -> viewModelScope.launch {
                signIn(result.credential).fold(
                    ifLeft = { error -> _uiState.update { it.withProviderError(error) } },
                    ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isSignedUp = true) } },
                )
            }
        }
    }

    override fun onSubmit() {
        val current = _uiState.value
        if (current.isLoading || current.isSignedUp) return

        // The password is left as typed: spaces can be part of it. The name and email are trimmed, since
        // keyboards and autofill often add a trailing space.
        val name = current.name.trim()
        val email = current.email.trim()
        val nameError = validateName(name)
        val emailError = validateEmail(email)
        val passwordError = validatePassword(current.password, passwordPolicy)
        if (nameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError)
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            signUp(name, email, current.password).fold(
                ifLeft = { error -> _uiState.update { it.withSignUpError(error) } },
                ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isSignedUp = true) } },
            )
        }
    }
}

private fun CreateAccountUiState.withFormError(error: FormError): CreateAccountUiState =
    copy(isLoading = false, formError = error)

private fun CreateAccountUiState.withSignUpError(error: SignInError): CreateAccountUiState = when (error) {
    is SignInError.Auth -> withAuthError(error.cause)

    // Offline mode couldn't be turned off, so no account was created. There's nothing the user can fix.
    is SignInError.Storage -> withFormError(FormError.Generic)
}

private fun CreateAccountUiState.withAuthError(error: AuthError): CreateAccountUiState = when (error) {
    AuthError.EmailAlreadyInUse -> copy(isLoading = false, emailError = EmailError.AlreadyInUse)

    AuthError.InvalidEmail -> copy(isLoading = false, emailError = EmailError.Invalid)

    AuthError.WeakPassword -> copy(isLoading = false, passwordError = PasswordError.Rejected)

    AuthError.Network -> copy(isLoading = false, formError = FormError.Network)

    AuthError.InvalidCredential,
    AuthError.AccountDisabled,
    AuthError.TooManyRequests,
    AuthError.Unknown,
    -> copy(isLoading = false, formError = FormError.Generic)
}

private fun CreateAccountUiState.withProviderError(error: SignInError): CreateAccountUiState = withFormError(
    when (error) {
        is SignInError.Storage -> FormError.SignInFailed

        is SignInError.Auth -> when (error.cause) {
            AuthError.Network -> FormError.Network

            AuthError.TooManyRequests,
            AuthError.AccountDisabled,
            AuthError.InvalidCredential,
            AuthError.EmailAlreadyInUse,
            AuthError.InvalidEmail,
            AuthError.WeakPassword,
            AuthError.Unknown,
            -> FormError.SignInFailed
        }
    },
)
