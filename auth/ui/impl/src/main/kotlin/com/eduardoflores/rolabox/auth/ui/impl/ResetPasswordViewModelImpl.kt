package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TICK_MILLIS = 1_000L

@HiltViewModel
internal class ResetPasswordViewModelImpl @Inject constructor(private val authRepository: AuthRepository) :
    ResetPasswordViewModel() {
    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    override val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null
    private var prefilled = false

    override fun prefill(email: String) {
        if (prefilled) return
        prefilled = true
        _uiState.update { it.copy(email = email) }
    }

    override fun onEmailChange(email: String) = _uiState.update {
        it.copy(email = email, emailError = null, error = null)
    }

    override fun onSend() {
        val current = _uiState.value
        if (current.isLoading) return

        // Keyboards and autofill often add a trailing space.
        val email = current.email.trim()
        val emailError = validateEmail(email)
        when {
            emailError != null -> _uiState.update { it.copy(emailError = emailError, error = null) }

            // Coming back to fix a typo and sending to the same address again must not skip the cooldown.
            email == current.sentTo && current.cooldownSeconds > 0 ->
                _uiState.update { it.copy(step = ResetPasswordStep.Sent, email = email, error = null) }

            else -> send(email)
        }
    }

    override fun onResend() {
        val current = _uiState.value
        if (!current.canResend) return
        send(current.sentTo)
    }

    override fun onEditEmail() = _uiState.update { it.copy(step = ResetPasswordStep.Email, error = null) }

    private fun send(email: String) {
        _uiState.update { it.copy(isLoading = true, emailError = null, error = null) }
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email).fold(
                ifLeft = { error ->
                    _uiState.update { it.withError(error) }
                    if (error == AuthError.TooManyRequests) startCooldown(TOO_MANY_REQUESTS_COOLDOWN_SECONDS)
                },
                ifRight = { _ ->
                    _uiState.update { state ->
                        state.copy(step = ResetPasswordStep.Sent, email = email, sentTo = email, isLoading = false)
                    }
                    startCooldown(RESEND_COOLDOWN_SECONDS)
                },
            )
        }
    }

    private fun ResetPasswordUiState.withError(error: AuthError): ResetPasswordUiState = when (error) {
        // The address is checked before the call, so this is an edge case. It's still about the field.
        AuthError.InvalidEmail -> copy(isLoading = false, emailError = EmailError.Invalid)

        AuthError.Network -> copy(isLoading = false, error = ResetPasswordError.Network)

        AuthError.TooManyRequests -> copy(isLoading = false, error = ResetPasswordError.TooManyRequests)

        // Sign-in and sign-up errors can't come from sending a link. Nothing the user can fix.
        AuthError.Unknown,
        AuthError.InvalidCredential,
        AuthError.AccountDisabled,
        AuthError.EmailAlreadyInUse,
        AuthError.WeakPassword,
        -> copy(isLoading = false, error = ResetPasswordError.Unknown)
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        _uiState.update { it.copy(cooldownSeconds = seconds) }
        cooldownJob = viewModelScope.launch {
            while (_uiState.value.cooldownSeconds > 0) {
                delay(TICK_MILLIS)
                _uiState.update { it.copy(cooldownSeconds = it.cooldownSeconds - 1) }
            }
        }
    }
}
