package com.eduardoflores.rolabox.feature.account

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.PasswordStrength
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.auth.testing.SignUpRequest
import com.eduardoflores.rolabox.core.auth.ui.SignInConfig
import com.eduardoflores.rolabox.core.auth.ui.SignInProvider
import com.eduardoflores.rolabox.core.auth.ui.SignInStepResult
import com.eduardoflores.rolabox.core.domain.SignInUseCase
import com.eduardoflores.rolabox.core.domain.SignUpUseCase
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateAccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()
    private val viewModel =
        CreateAccountViewModel(
            SignUpUseCase(authRepository, userDataRepository),
            SignInUseCase(authRepository, userDataRepository),
            SignInConfig(googleWebClientId = "web-client-id"),
        )
    private val state get() = viewModel.uiState.value

    private fun fill(
        name: String = "Eduardo",
        email: String = "toomanyeduardos@gmail.com",
        password: String = "kdjfhqPwzm4x!",
    ) {
        viewModel.onNameChange(name)
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun startsEmpty() {
        assertEquals(CreateAccountUiState(), state)
        assertEquals(PasswordStrength.Empty, state.strength)
    }

    @Test
    fun password_strengthUpdatesWithEveryChange() {
        viewModel.onPasswordChange("abc")
        assertEquals(PasswordStrength.Weak, state.strength)

        viewModel.onPasswordChange("kdjfhqPwzm4x")
        assertEquals(PasswordStrength.Good, state.strength)

        viewModel.onPasswordChange("")
        assertEquals(PasswordStrength.Empty, state.strength)
    }

    @Test
    fun submit_withNothingFilledInShowsEveryFieldError() {
        viewModel.onSubmit()

        assertEquals(NameError.Required, state.nameError)
        assertEquals(EmailError.Required, state.emailError)
        assertEquals(PasswordError.TooShort, state.passwordError)
        assertNull(authRepository.lastSignUp)
    }

    @Test
    fun submit_belowTheMinimumLengthIsBlocked() {
        fill(password = "1234567")

        viewModel.onSubmit()

        assertEquals(PasswordError.TooShort, state.passwordError)
        assertNull(state.nameError)
        assertNull(state.emailError)
        assertNull(authRepository.lastSignUp)
        assertFalse(state.isLoading)
    }

    @Test
    fun submit_withoutEveryCharacterKindIsBlocked() {
        fill(password = "kdjfhqPwzm4x")

        viewModel.onSubmit()

        assertEquals(PasswordError.MissingCharacters, state.passwordError)
        assertNull(authRepository.lastSignUp)
    }

    @Test
    fun submit_withMalformedEmailIsBlocked() {
        fill(email = "toomanyeduardos@gmail")

        viewModel.onSubmit()

        assertEquals(EmailError.Invalid, state.emailError)
        assertNull(authRepository.lastSignUp)
    }

    @Test
    fun editingAFieldClearsItsError() {
        viewModel.onSubmit()

        viewModel.onNameChange("A")
        assertNull(state.nameError)
        assertEquals(EmailError.Required, state.emailError)

        viewModel.onEmailChange("a")
        assertNull(state.emailError)

        viewModel.onPasswordChange("p")
        assertNull(state.passwordError)
    }

    @Test
    fun submit_signsUpWithTrimmedNameAndEmailAndTheUntouchedPassword() = runTest {
        fill(name = "  Eduardo Flores ", email = " toomanyeduardos@gmail.com ", password = " kdjfhqPwzm4x! ")

        viewModel.onSubmit()

        assertEquals(
            SignUpRequest(
                "Eduardo Flores",
                "toomanyeduardos@gmail.com",
                " kdjfhqPwzm4x! ",
            ),
            authRepository.lastSignUp,
        )
        assertTrue(state.isSignedUp)
        assertFalse(state.isLoading)
        assertEquals(
            "Eduardo Flores",
            (authRepository.observeAuthState().first() as AuthState.SignedIn).user.displayName,
        )
    }

    @Test
    fun submit_afterSigningUpDoesNotSignUpAgain() = runTest {
        fill()
        viewModel.onSubmit()
        authRepository.setAuthState(AuthState.SignedOut)

        viewModel.onSubmit()

        assertEquals(AuthState.SignedOut, authRepository.observeAuthState().first())
    }

    @Test
    fun emailAlreadyInUse_isAnErrorOnTheEmailField() {
        authRepository.signUpError = AuthError.EmailAlreadyInUse
        fill()

        viewModel.onSubmit()

        assertEquals(EmailError.AlreadyInUse, state.emailError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
        assertNull(state.formError)
    }

    @Test
    fun emailAlreadyInUse_clearsWhenTheEmailIsEdited() {
        authRepository.signUpError = AuthError.EmailAlreadyInUse
        fill()
        viewModel.onSubmit()

        viewModel.onEmailChange("other@mail.com")

        assertNull(state.emailError)
    }

    @Test
    fun invalidEmailFromTheBackend_isAnErrorOnTheEmailField() {
        authRepository.signUpError = AuthError.InvalidEmail
        fill()

        viewModel.onSubmit()

        assertEquals(EmailError.Invalid, state.emailError)
    }

    @Test
    fun weakPasswordFromTheBackend_isAnErrorOnThePasswordField() {
        authRepository.signUpError = AuthError.WeakPassword
        fill()

        viewModel.onSubmit()

        assertEquals(PasswordError.Rejected, state.passwordError)
        assertFalse(state.isLoading)
    }

    @Test
    fun network_isAFormError() {
        authRepository.signUpError = AuthError.Network
        fill()

        viewModel.onSubmit()

        assertEquals(FormError.Network, state.formError)
        assertFalse(state.isLoading)
    }

    @Test
    fun network_canBeRetriedAndClearsOnSuccess() {
        authRepository.signUpError = AuthError.Network
        fill()
        viewModel.onSubmit()

        authRepository.signUpError = null
        viewModel.onSubmit()

        assertNull(state.formError)
        assertTrue(state.isSignedUp)
    }

    @Test
    fun submit_fromOfflineMode_leavesOfflineMode() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        fill()

        viewModel.onSubmit()

        assertTrue(state.isSignedUp)
        assertEquals(false, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun submit_whenOfflineModeCantBeTurnedOff_isAGenericFormErrorAndNoAccountIsCreated() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.writeError = StorageError.Unavailable
        fill()

        viewModel.onSubmit()

        assertEquals(FormError.Generic, state.formError)
        assertNull(authRepository.lastSignUp)
        assertFalse(state.isLoading)
    }

    @Test
    fun otherErrors_areAGenericFormError() {
        listOf(
            AuthError.InvalidCredential,
            AuthError.AccountDisabled,
            AuthError.TooManyRequests,
            AuthError.Unknown,
        ).forEach { error ->
            authRepository.signUpError = error
            fill()

            viewModel.onSubmit()

            assertEquals(FormError.Generic, state.formError)
            assertFalse(state.isLoading)
        }
    }

    private fun google(result: SignInStepResult) {
        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onSignInResult(result)
    }

    @Test
    fun google_success_exchangesTheTokenAndSignsUp() {
        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(SignInCredential.GoogleIdToken("id-token"), authRepository.lastSignInCredential)
        assertTrue(state.isSignedUp)
        assertFalse(state.isLoading)
        assertNull(state.requestedProvider)
        assertNull(state.formError)
    }

    @Test
    fun google_cancel_returnsSilently() {
        google(SignInStepResult.Cancelled)

        assertNull(state.formError)
        assertNull(authRepository.lastSignInCredential)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
        assertNull(state.requestedProvider)
    }

    @Test
    fun google_noAccountOnTheDevice_isAFormError() {
        google(SignInStepResult.NoAccount)

        assertEquals(FormError.NoAccount, state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
    }

    @Test
    fun google_failures_areFormErrors() {
        mapOf(
            AuthError.Network to FormError.Network,
            AuthError.TooManyRequests to FormError.SignInFailed,
            AuthError.AccountDisabled to FormError.SignInFailed,
            AuthError.Unknown to FormError.SignInFailed,
        ).forEach { (error, formError) ->
            authRepository.signInError = error

            google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

            assertEquals("$error", formError, state.formError)
            assertFalse("$error", state.isLoading)
            assertFalse("$error", state.isSignedUp)
        }
        google(SignInStepResult.Failed)
        assertEquals(FormError.SignInFailed, state.formError)
    }

    @Test
    fun google_cancelFromOfflineMode_keepsOfflineMode() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        google(SignInStepResult.Cancelled)

        assertEquals(true, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun google_whenOfflineModeCantBeTurnedOff_isAFailureWithoutCallingTheBackend() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.writeError = StorageError.Unavailable

        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(FormError.SignInFailed, state.formError)
        assertNull(authRepository.lastSignInCredential)
        assertFalse(state.isLoading)
    }

    @Test
    fun google_keepsWhatWasTypedInTheForm() {
        fill()

        google(SignInStepResult.NoAccount)

        assertEquals("Eduardo", state.name)
        assertEquals("toomanyeduardos@gmail.com", state.email)
    }

    @Test
    fun google_isIgnoredWhileLoading() {
        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onProviderClick(SignInProvider.Google)

        assertEquals(SignInProvider.Google, state.requestedProvider)
        assertTrue(state.isLoading)
    }
}
