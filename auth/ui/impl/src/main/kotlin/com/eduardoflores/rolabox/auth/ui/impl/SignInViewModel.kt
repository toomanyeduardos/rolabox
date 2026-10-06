package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import kotlinx.coroutines.flow.StateFlow

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
    /** A provider's account picker is due. The route shows it and reports the result. */
    val signInRequest: SignInRequest? = null,
)

/** What the Sign in screen shows, and what the user does on it (ADR-021). [DefaultSignInViewModel] implements it. */
internal abstract class SignInViewModel : ViewModel() {
    abstract val uiState: StateFlow<SignInUiState>

    abstract fun onEmailChange(email: String)

    abstract fun onPasswordChange(password: String)

    abstract fun onSubmit()

    /** Starts signing in with [provider]. The route shows its picker when [SignInUiState.signInRequest] is set. */
    abstract fun onProviderClick(provider: SignInProvider)

    /** The account picker is done. A [SignInStepResult.Credential] is exchanged for a session. */
    abstract fun onSignInResult(result: SignInStepResult)

    /** Goes on without an account (ADR-008). The choice is kept on this device, so it's asked once. */
    abstract fun onOfflineClick()
}
