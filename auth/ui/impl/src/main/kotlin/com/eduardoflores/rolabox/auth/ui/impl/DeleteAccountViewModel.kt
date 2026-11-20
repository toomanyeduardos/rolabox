package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import kotlinx.coroutines.flow.StateFlow

internal data class DeleteAccountUiState(
    /** The account has a password, so the screen asks for it (ADR-037). */
    val showsPassword: Boolean = false,
    /** The account signs in with Google, so the screen offers its button. Both when linked (ADR-013). */
    val showsGoogle: Boolean = false,
    val password: String = "",
    val passwordError: DeleteAccountPasswordError? = null,
    val formError: DeleteAccountFormError? = null,
    val isLoading: Boolean = false,
    /** Set while Google's account picker is due (ADR-009). */
    val signInRequest: SignInRequest? = null,
    /** The account is deleted, and the screen should be left for Sign in. */
    val isFinished: Boolean = false,
)

internal enum class DeleteAccountPasswordError { Required, Wrong }

internal enum class DeleteAccountFormError {
    /** Offline, or the backend couldn't be reached. Nothing was deleted, or a retry finishes it. */
    Network,

    /** The backend wants a more recent sign-in, even after this one. Re-authenticating, then retrying, works. */
    SignInAgain,

    /** Google gave a credential of another account than the one being deleted. */
    OtherAccount,

    /** Google has no account on the device to pick. */
    NoAccount,

    /** Anything else: the account wasn't deleted, and there's nothing the user can fix. */
    Unknown,
}

/**
 * What the Delete account screen shows, and what the user does on it (ADR-021, ADR-037).
 * [DeleteAccountViewModelImpl] implements it.
 */
internal abstract class DeleteAccountViewModel : ViewModel() {
    abstract val uiState: StateFlow<DeleteAccountUiState>

    abstract fun onPasswordChange(password: String)

    /** Re-authenticates with the typed password, then deletes the account. */
    abstract fun onDeleteClick()

    /** Re-authenticates with [provider]'s account picker, then deletes the account. */
    abstract fun onProviderClick(provider: SignInProvider)

    abstract fun onSignInResult(result: SignInStepResult)
}
