package com.eduardoflores.rolabox.auth.ui.impl

import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.PasswordStrength
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.testing.FakeSignInUseCase
import com.eduardoflores.rolabox.auth.data.testing.FakeSignUpUseCase
import com.eduardoflores.rolabox.auth.data.testing.SignUpRequest
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// The use cases are fakes (ADR-021): what they do about offline mode (ADR-008, rule 7) is tested in
// :auth:data:impl. These tests cover what the screen does with each answer.
class CreateAccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signUp = FakeSignUpUseCase()
    private val signIn = FakeSignInUseCase()
    private val viewModel = DefaultCreateAccountViewModel(signUp, signIn, SIGN_IN_CONFIG)
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
        assertNull(signUp.lastRequest)
    }

    @Test
    fun submit_belowTheMinimumLengthIsBlocked() {
        fill(password = "1234567")

        viewModel.onSubmit()

        assertEquals(PasswordError.TooShort, state.passwordError)
        assertNull(state.nameError)
        assertNull(state.emailError)
        assertNull(signUp.lastRequest)
        assertFalse(state.isLoading)
    }

    @Test
    fun submit_withoutEveryCharacterKindIsBlocked() {
        fill(password = "kdjfhqPwzm4x")

        viewModel.onSubmit()

        assertEquals(PasswordError.MissingCharacters, state.passwordError)
        assertNull(signUp.lastRequest)
    }

    @Test
    fun submit_withMalformedEmailIsBlocked() {
        fill(email = "toomanyeduardos@gmail")

        viewModel.onSubmit()

        assertEquals(EmailError.Invalid, state.emailError)
        assertNull(signUp.lastRequest)
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
            signUp.lastRequest,
        )
        assertTrue(state.isSignedUp)
        assertFalse(state.isLoading)
    }

    @Test
    fun submit_afterSigningUpDoesNotSignUpAgain() = runTest {
        fill()
        viewModel.onSubmit()
        viewModel.onNameChange("Someone Else")

        viewModel.onSubmit()

        assertEquals("Eduardo", signUp.lastRequest?.name)
    }

    @Test
    fun emailAlreadyInUse_isAnErrorOnTheEmailField() {
        signUp.error = SignInError.Auth(AuthError.EmailAlreadyInUse)
        fill()

        viewModel.onSubmit()

        assertEquals(EmailError.AlreadyInUse, state.emailError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
        assertNull(state.formError)
    }

    @Test
    fun emailAlreadyInUse_clearsWhenTheEmailIsEdited() {
        signUp.error = SignInError.Auth(AuthError.EmailAlreadyInUse)
        fill()
        viewModel.onSubmit()

        viewModel.onEmailChange("other@mail.com")

        assertNull(state.emailError)
    }

    @Test
    fun invalidEmailFromTheBackend_isAnErrorOnTheEmailField() {
        signUp.error = SignInError.Auth(AuthError.InvalidEmail)
        fill()

        viewModel.onSubmit()

        assertEquals(EmailError.Invalid, state.emailError)
    }

    @Test
    fun weakPasswordFromTheBackend_isAnErrorOnThePasswordField() {
        signUp.error = SignInError.Auth(AuthError.WeakPassword)
        fill()

        viewModel.onSubmit()

        assertEquals(PasswordError.Rejected, state.passwordError)
        assertFalse(state.isLoading)
    }

    @Test
    fun network_isAFormError() {
        signUp.error = SignInError.Auth(AuthError.Network)
        fill()

        viewModel.onSubmit()

        assertEquals(FormError.Network, state.formError)
        assertFalse(state.isLoading)
    }

    @Test
    fun network_canBeRetriedAndClearsOnSuccess() {
        signUp.error = SignInError.Auth(AuthError.Network)
        fill()
        viewModel.onSubmit()

        signUp.error = null
        viewModel.onSubmit()

        assertNull(state.formError)
        assertTrue(state.isSignedUp)
    }

    @Test
    fun submit_whenOfflineModeCantBeTurnedOff_isAGenericFormError() {
        signUp.error = SignInError.Storage(StorageError.Unavailable)
        fill()

        viewModel.onSubmit()

        assertEquals(FormError.Generic, state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
    }

    @Test
    fun otherErrors_areAGenericFormError() {
        listOf(
            AuthError.InvalidCredential,
            AuthError.AccountDisabled,
            AuthError.TooManyRequests,
            AuthError.Unknown,
        ).forEach { error ->
            signUp.error = SignInError.Auth(error)
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

        assertEquals(SignInCredential.GoogleIdToken("id-token"), signIn.lastCredential)
        assertTrue(state.isSignedUp)
        assertFalse(state.isLoading)
        assertNull(state.signInRequest)
        assertNull(state.formError)
    }

    @Test
    fun google_cancel_returnsSilently() {
        google(SignInStepResult.Cancelled)

        assertNull(state.formError)
        assertNull(signIn.lastCredential)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
        assertNull(state.signInRequest)
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
            signIn.credentialError = SignInError.Auth(error)

            google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

            assertEquals("$error", formError, state.formError)
            assertFalse("$error", state.isLoading)
            assertFalse("$error", state.isSignedUp)
        }
        google(SignInStepResult.Failed)
        assertEquals(FormError.SignInFailed, state.formError)
    }

    @Test
    fun google_whenOfflineModeCantBeTurnedOff_isAFailure() {
        signIn.credentialError = SignInError.Storage(StorageError.Unavailable)

        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(FormError.SignInFailed, state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedUp)
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

        assertEquals(SignInRequest(SignInProvider.Google, SIGN_IN_CONFIG), state.signInRequest)
        assertTrue(state.isLoading)
    }
}

private val SIGN_IN_CONFIG = SignInConfig(googleWebClientId = "web-client-id")
