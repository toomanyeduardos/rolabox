package com.eduardoflores.rolabox.feature.account

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.PasswordStrength
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.auth.testing.SignUpRequest
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
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
    private val viewModel = CreateAccountViewModel(authRepository)
    private val state get() = viewModel.uiState.value

    private fun fill(
        name: String = "Eduardo",
        email: String = "toomanyeduardos@gmail.com",
        password: String = "kdjfhqPwzm4x",
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
        assertEquals(PasswordStrength.Strong, state.strength)

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
        fill(name = "  Eduardo Flores ", email = " toomanyeduardos@gmail.com ", password = " kdjfhqPwzm4x ")

        viewModel.onSubmit()

        assertEquals(
            SignUpRequest(
                "Eduardo Flores",
                "toomanyeduardos@gmail.com",
                " kdjfhqPwzm4x ",
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
    fun otherErrors_areAGenericFormError() {
        listOf(AuthError.InvalidCredential, AuthError.AccountDisabled, AuthError.Unavailable).forEach { error ->
            authRepository.signUpError = error
            fill()

            viewModel.onSubmit()

            assertEquals(FormError.Generic, state.formError)
            assertFalse(state.isLoading)
        }
    }
}
