package com.eduardoflores.rolabox.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.PasswordPolicy
import com.eduardoflores.rolabox.core.auth.api.PasswordStrength
import com.eduardoflores.rolabox.core.auth.ui.SignInConfig
import com.eduardoflores.rolabox.core.auth.ui.SignInProvider
import com.eduardoflores.rolabox.core.auth.ui.SignInStepResult
import com.eduardoflores.rolabox.core.domain.SignInError
import com.eduardoflores.rolabox.core.domain.SignInUseCase
import com.eduardoflores.rolabox.core.domain.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An error that isn't about one field. */
internal enum class FormError { Network, NoAccount, SignInFailed, Generic }

internal data class CreateAccountUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val strength: PasswordStrength = PasswordStrength.Empty,
    val nameError: NameError? = null,
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
    val formError: FormError? = null,
    val isLoading: Boolean = false,
    /** The account was created and the user is signed in. The screen reports it once and leaves. */
    val isSignedUp: Boolean = false,
    /** This provider's account picker is due. The route shows it and reports the result. */
    val requestedProvider: SignInProvider? = null,
)

@HiltViewModel
internal class CreateAccountViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    private val signIn: SignInUseCase,
    val signInConfig: SignInConfig,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    // Editing a field clears what was said about it, since that error no longer describes the text.
    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, nameError = null, formError = null) }

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, emailError = null, formError = null) }

    fun onPasswordChange(password: String) = _uiState.update {
        it.copy(
            password = password,
            strength = PasswordPolicy.strengthOf(password),
            passwordError = null,
            formError = null,
        )
    }

    /** Starts signing in with [provider], which creates the account if there isn't one. The route shows the picker. */
    fun onProviderClick(provider: SignInProvider) {
        val current = _uiState.value
        if (current.isLoading || current.isSignedUp) return

        _uiState.update { it.copy(isLoading = true, formError = null, requestedProvider = provider) }
    }

    /** The account picker is done. A [SignInStepResult.Credential] is exchanged for a session. */
    fun onSignInResult(result: SignInStepResult) {
        if (_uiState.value.requestedProvider == null) return

        _uiState.update { it.copy(requestedProvider = null) }
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

    fun onSubmit() {
        val current = _uiState.value
        if (current.isLoading || current.isSignedUp) return

        // The password is left as typed: spaces can be part of it. The name and email are trimmed, since
        // keyboards and autofill often add a trailing space.
        val name = current.name.trim()
        val email = current.email.trim()
        val nameError = validateName(name)
        val emailError = validateEmail(email)
        val passwordError = validatePassword(current.password)
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
