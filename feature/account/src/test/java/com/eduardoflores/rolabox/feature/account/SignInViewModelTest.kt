package com.eduardoflores.rolabox.feature.account

import arrow.core.Either
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.auth.testing.SignInWithEmailRequest
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val viewModel = SignInViewModel(authRepository)
    private val state get() = viewModel.uiState.value

    private fun fill(email: String = "toomanyeduardos@gmail.com", password: String = "secret") {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun startsEmpty() {
        assertEquals(SignInUiState(), state)
    }

    @Test
    fun submit_withNothingFilledInShowsBothFieldErrorsAndDoesNotCallTheBackend() {
        viewModel.onSubmit()

        assertEquals(EmailError.Required, state.emailError)
        assertEquals(SignInPasswordError.Required, state.passwordError)
        assertNull(authRepository.lastSignInWithEmail)
        assertFalse(state.isLoading)
    }

    @Test
    fun submit_withMalformedEmailIsBlocked() {
        fill(email = "toomanyeduardos@gmail")

        viewModel.onSubmit()

        assertEquals(EmailError.Invalid, state.emailError)
        assertNull(state.passwordError)
        assertNull(authRepository.lastSignInWithEmail)
    }

    @Test
    fun submit_withAnEmptyPasswordIsBlocked() {
        fill(password = "")

        viewModel.onSubmit()

        assertEquals(SignInPasswordError.Required, state.passwordError)
        assertNull(state.emailError)
        assertNull(authRepository.lastSignInWithEmail)
    }

    @Test
    fun submit_doesNotApplyTheSignUpPasswordRules() {
        fill(password = "a")

        viewModel.onSubmit()

        assertNull(state.passwordError)
        assertEquals(SignInWithEmailRequest("toomanyeduardos@gmail.com", "a"), authRepository.lastSignInWithEmail)
    }

    @Test
    fun submit_signsInWithATrimmedEmailAndTheUntouchedPassword() = runTest {
        fill(email = " toomanyeduardos@gmail.com ", password = " secret ")

        viewModel.onSubmit()

        assertEquals(
            SignInWithEmailRequest("toomanyeduardos@gmail.com", " secret "),
            authRepository.lastSignInWithEmail,
        )
        assertTrue(state.isSignedIn)
        assertFalse(state.isLoading)
        assertTrue(authRepository.observeAuthState().first() is AuthState.SignedIn)
    }

    @Test
    fun submit_afterSigningInDoesNotSignInAgain() = runTest {
        fill()
        viewModel.onSubmit()
        authRepository.setAuthState(AuthState.SignedOut)

        viewModel.onSubmit()

        assertEquals(AuthState.SignedOut, authRepository.observeAuthState().first())
    }

    @Test
    fun submit_whileLoadingDoesNotSubmitAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val slowRepository = object : AuthRepository by authRepository {
            override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, AuthUser> {
                calls++
                gate.await()
                return authRepository.signInWithEmail(email, password)
            }
        }
        val slowViewModel = SignInViewModel(slowRepository)
        slowViewModel.onEmailChange("toomanyeduardos@gmail.com")
        slowViewModel.onPasswordChange("secret")

        slowViewModel.onSubmit()
        assertTrue(slowViewModel.uiState.value.isLoading)
        slowViewModel.onSubmit()
        slowViewModel.onSubmit()
        gate.complete(Unit)

        assertEquals(1, calls)
        assertFalse(slowViewModel.uiState.value.isLoading)
        assertTrue(slowViewModel.uiState.value.isSignedIn)
    }

    @Test
    fun badCredentials_areOneGenericErrorOnThePasswordField() {
        authRepository.signInWithEmailError = AuthError.InvalidCredential
        fill()

        viewModel.onSubmit()

        assertEquals(SignInPasswordError.BadLogin, state.passwordError)
        assertNull(state.emailError)
        assertNull(state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedIn)
    }

    @Test
    fun invalidEmailFromTheBackend_isTheSameGenericError() {
        authRepository.signInWithEmailError = AuthError.InvalidEmail
        fill()

        viewModel.onSubmit()

        assertEquals(SignInPasswordError.BadLogin, state.passwordError)
        assertNull(state.emailError)
    }

    @Test
    fun otherErrors_areFormErrors() {
        mapOf(
            AuthError.Network to SignInFormError.Network,
            AuthError.TooManyRequests to SignInFormError.TooManyRequests,
            AuthError.AccountDisabled to SignInFormError.AccountDisabled,
            AuthError.Unknown to SignInFormError.Unknown,
            AuthError.EmailAlreadyInUse to SignInFormError.Unknown,
            AuthError.WeakPassword to SignInFormError.Unknown,
        ).forEach { (error, formError) ->
            authRepository.signInWithEmailError = error
            fill()

            viewModel.onSubmit()

            assertEquals("$error", formError, state.formError)
            assertNull("$error", state.passwordError)
            assertFalse("$error", state.isLoading)
        }
    }

    @Test
    fun editingAFieldClearsItsError() {
        viewModel.onSubmit()

        viewModel.onEmailChange("a")
        assertNull(state.emailError)
        assertEquals(SignInPasswordError.Required, state.passwordError)

        viewModel.onPasswordChange("p")
        assertNull(state.passwordError)
    }

    @Test
    fun editingEitherFieldClearsABadLogin() {
        authRepository.signInWithEmailError = AuthError.InvalidCredential
        fill()
        viewModel.onSubmit()
        viewModel.onEmailChange("other@mail.com")
        assertNull(state.passwordError)

        viewModel.onSubmit()
        assertEquals(SignInPasswordError.BadLogin, state.passwordError)
        viewModel.onPasswordChange("other")
        assertNull(state.passwordError)
    }

    @Test
    fun editingAFieldClearsAFormError() {
        authRepository.signInWithEmailError = AuthError.Network
        fill()
        viewModel.onSubmit()

        viewModel.onPasswordChange("other")

        assertNull(state.formError)
    }

    @Test
    fun aFailedSignInCanBeRetriedAndSucceeds() {
        authRepository.signInWithEmailError = AuthError.Network
        fill()
        viewModel.onSubmit()

        authRepository.signInWithEmailError = null
        viewModel.onSubmit()

        assertNull(state.formError)
        assertTrue(state.isSignedIn)
    }
}
