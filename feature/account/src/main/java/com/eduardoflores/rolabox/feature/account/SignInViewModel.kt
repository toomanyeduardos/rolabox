package com.eduardoflores.rolabox.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.ui.SignInConfig
import com.eduardoflores.rolabox.core.auth.ui.SignInError
import com.eduardoflores.rolabox.core.auth.ui.SignInFlow
import com.eduardoflores.rolabox.core.auth.ui.SignInOutcome
import com.eduardoflores.rolabox.core.auth.ui.SignInProvider
import com.eduardoflores.rolabox.core.auth.ui.SignInStepResult
import com.eduardoflores.rolabox.core.domain.LeaveOfflineModeUseCase
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class SignInPasswordError {
    Required,

    /**
     * The email or the password is wrong, and we can't say which: with email enumeration protection
     * on, Firebase doesn't tell them apart, and the screen must not either.
     */
    BadLogin,
}

/** An error that isn't about one field. */
internal enum class SignInFormError { Network, TooManyRequests, AccountDisabled, NoAccount, Unknown }

internal data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailError: EmailError? = null,
    val passwordError: SignInPasswordError? = null,
    val formError: SignInFormError? = null,
    val isLoading: Boolean = false,
    /** The user signed in, or chose to go on without an account. The screen reports it once and leaves. */
    val isFinished: Boolean = false,
    /** Offline mode is off and this provider's account picker is due. The route shows it and reports the result. */
    val requestedProvider: SignInProvider? = null,
)

@HiltViewModel
internal class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
    leaveOfflineMode: LeaveOfflineModeUseCase,
    val signInConfig: SignInConfig,
) : ViewModel() {
    private val signInFlow = SignInFlow(authRepository, leaveOfflineMode)
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    // Editing a field clears what was said about it. A bad login is about both fields, so editing
    // either one clears it, and so does the LCD error that goes with it.
    fun onEmailChange(email: String) = _uiState.update {
        it.copy(email = email, emailError = null, passwordError = it.passwordError.unlessBadLogin(), formError = null)
    }

    fun onPasswordChange(password: String) = _uiState.update {
        it.copy(password = password, passwordError = null, formError = null)
    }

    fun onSubmit() {
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
            authRepository.signInWithEmail(email, current.password).fold(
                ifLeft = { error -> _uiState.update { it.withError(error) } },
                ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isFinished = true) } },
            )
        }
    }

    /** Starts signing in with [provider]. The route shows its picker when [SignInUiState.requestedProvider] is set. */
    fun onProviderClick(provider: SignInProvider) {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return

        _uiState.update { it.copy(isLoading = true, emailError = null, passwordError = null, formError = null) }
        viewModelScope.launch {
            val error = signInFlow.prepare()
            _uiState.update { if (error == null) it.copy(requestedProvider = provider) else it.withSignInError(error) }
        }
    }

    /** The account picker is done. A [SignInStepResult.Credential] is exchanged for a session. */
    fun onSignInResult(result: SignInStepResult) {
        if (_uiState.value.requestedProvider == null) return

        _uiState.update { it.copy(requestedProvider = null) }
        viewModelScope.launch {
            when (val outcome = signInFlow.complete(result)) {
                SignInOutcome.SignedIn -> _uiState.update { it.copy(isLoading = false, isFinished = true) }
                SignInOutcome.Cancelled -> _uiState.update { it.copy(isLoading = false) }
                is SignInOutcome.Failed -> _uiState.update { it.withSignInError(outcome.error) }
            }
        }
    }

    /** Goes on without an account (ADR-008). The choice is kept on this device, so it's asked once. */
    fun onOfflineClick() {
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

private fun SignInUiState.withError(error: AuthError): SignInUiState = when (error) {
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

private fun SignInUiState.withSignInError(error: SignInError): SignInUiState = copy(
    isLoading = false,
    formError = when (error) {
        SignInError.Network -> SignInFormError.Network
        SignInError.TooManyRequests -> SignInFormError.TooManyRequests
        SignInError.AccountDisabled -> SignInFormError.AccountDisabled
        SignInError.NoAccount -> SignInFormError.NoAccount
        SignInError.Unknown -> SignInFormError.Unknown
    },
)
