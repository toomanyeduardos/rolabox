package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

internal enum class ResetPasswordStep { Email, Sent }

/** An error that isn't about the email field. */
internal enum class ResetPasswordError { Network, TooManyRequests, Unknown }

internal data class ResetPasswordUiState(
    val step: ResetPasswordStep = ResetPasswordStep.Email,
    val email: String = "",
    val emailError: EmailError? = null,
    val error: ResetPasswordError? = null,
    val isLoading: Boolean = false,
    /** The address the last link went to. It's what "Resend link" sends to, whatever [email] says now. */
    val sentTo: String = "",
    /** Seconds until the link can be sent again. 0 means it can. */
    val cooldownSeconds: Int = 0,
) {
    val canResend: Boolean get() = cooldownSeconds == 0 && !isLoading
}

/**
 * What the Reset password screen shows, and what the user does on it (ADR-021).
 * [DefaultResetPasswordViewModel] implements it.
 */
internal abstract class ResetPasswordViewModel : ViewModel() {
    abstract val uiState: StateFlow<ResetPasswordUiState>

    /** Pre-fills the email the user typed on Sign in. Only the first call counts, so it never overwrites edits. */
    abstract fun prefill(email: String)

    abstract fun onEmailChange(email: String)

    /** Step 1: sends the link to the typed email. */
    abstract fun onSend()

    /** Step 2: sends the link again to the address it went to. Ignored while the cooldown runs. */
    abstract fun onResend()

    /** Back from step 2 to step 1, to fix the address. The cooldown keeps running. */
    abstract fun onEditEmail()
}
