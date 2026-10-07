package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.auth.data.api.PasswordStrength
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import kotlinx.coroutines.flow.StateFlow

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
    /** A provider's account picker is due. The route shows it and reports the result. */
    val signInRequest: SignInRequest? = null,
)

/**
 * What the Create account screen shows, and what the user does on it (ADR-021).
 * [CreateAccountViewModelImpl] implements it.
 */
internal abstract class CreateAccountViewModel : ViewModel() {
    abstract val uiState: StateFlow<CreateAccountUiState>

    abstract fun onNameChange(name: String)

    abstract fun onEmailChange(email: String)

    abstract fun onPasswordChange(password: String)

    /** Starts signing in with [provider], which creates the account if there isn't one. The route shows the picker. */
    abstract fun onProviderClick(provider: SignInProvider)

    /** The account picker is done. A [SignInStepResult.Credential] is exchanged for a session. */
    abstract fun onSignInResult(result: SignInStepResult)

    abstract fun onSubmit()
}
