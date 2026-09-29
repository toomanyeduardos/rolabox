package com.eduardoflores.rolabox.feature.account

import arrow.core.Either
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.auth.testing.SignInWithEmailRequest
import com.eduardoflores.rolabox.core.auth.ui.SignInConfig
import com.eduardoflores.rolabox.core.auth.ui.SignInProvider
import com.eduardoflores.rolabox.core.auth.ui.SignInStepResult
import com.eduardoflores.rolabox.core.domain.SignInUseCase
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
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
    private val userDataRepository = FakeUserDataRepository()
    private val viewModel =
        SignInViewModel(SignInUseCase(authRepository, userDataRepository), userDataRepository, SIGN_IN_CONFIG)
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
        assertTrue(state.isFinished)
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
        val slowViewModel =
            SignInViewModel(
                SignInUseCase(slowRepository, userDataRepository),
                userDataRepository,
                SIGN_IN_CONFIG,
            )
        slowViewModel.onEmailChange("toomanyeduardos@gmail.com")
        slowViewModel.onPasswordChange("secret")

        slowViewModel.onSubmit()
        assertTrue(slowViewModel.uiState.value.isLoading)
        slowViewModel.onSubmit()
        slowViewModel.onSubmit()
        gate.complete(Unit)

        assertEquals(1, calls)
        assertFalse(slowViewModel.uiState.value.isLoading)
        assertTrue(slowViewModel.uiState.value.isFinished)
    }

    @Test
    fun submit_fromOfflineMode_leavesOfflineMode() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        fill()

        viewModel.onSubmit()

        assertTrue(state.isFinished)
        assertEquals(false, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun submit_whenOfflineModeCantBeTurnedOff_isAFormErrorAndDoesNotCallTheBackend() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.writeError = StorageError.Unavailable
        fill()

        viewModel.onSubmit()

        assertEquals(SignInFormError.Unknown, state.formError)
        assertNull(authRepository.lastSignInWithEmail)
        assertFalse(state.isFinished)
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
        assertFalse(state.isFinished)
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
        assertTrue(state.isFinished)
    }

    @Test
    fun offline_savesTheChoiceAndFinishes() = runTest {
        viewModel.onOfflineClick()

        assertTrue(userDataRepository.observeOfflineModeChosen().first().getOrNull() == true)
        assertTrue(state.isFinished)
        assertFalse(state.isLoading)
    }

    @Test
    fun offline_whenSavingFailsStaysOnTheScreenWithAnError() {
        userDataRepository.writeError = StorageError.Unavailable

        viewModel.onOfflineClick()

        assertFalse(state.isFinished)
        assertEquals(SignInFormError.Unknown, state.formError)
    }

    private fun google(result: SignInStepResult) {
        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onSignInResult(result)
    }

    @Test
    fun google_asksForThePickerWithoutLeavingOfflineModeYet() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        viewModel.onProviderClick(SignInProvider.Google)

        assertEquals(SignInProvider.Google, state.requestedProvider)
        assertTrue(state.isLoading)
        assertEquals(true, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun google_successFromOfflineMode_leavesOfflineMode() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertTrue(state.isFinished)
        assertEquals(false, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun google_cancelFromOfflineMode_keepsOfflineMode() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        google(SignInStepResult.Cancelled)

        assertEquals(true, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun google_success_exchangesTheTokenAndFinishes() {
        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(SignInCredential.GoogleIdToken("id-token"), authRepository.lastSignInCredential)
        assertTrue(state.isFinished)
        assertFalse(state.isLoading)
        assertNull(state.requestedProvider)
        assertNull(state.formError)
    }

    @Test
    fun google_cancel_returnsSilently() {
        google(SignInStepResult.Cancelled)

        assertNull(state.formError)
        assertNull(state.passwordError)
        assertNull(authRepository.lastSignInCredential)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
        assertNull(state.requestedProvider)
    }

    @Test
    fun google_noAccountOnTheDevice_isAFormError() {
        google(SignInStepResult.NoAccount)

        assertEquals(SignInFormError.NoAccount, state.formError)
        assertNull(authRepository.lastSignInCredential)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
    }

    @Test
    fun google_pickerFailure_isAnUnknownFormError() {
        google(SignInStepResult.Failed)

        assertEquals(SignInFormError.Unknown, state.formError)
        assertFalse(state.isLoading)
    }

    @Test
    fun google_backendErrors_areFormErrors() {
        mapOf(
            AuthError.Network to SignInFormError.Network,
            AuthError.TooManyRequests to SignInFormError.TooManyRequests,
            AuthError.AccountDisabled to SignInFormError.AccountDisabled,
            AuthError.InvalidCredential to SignInFormError.Unknown,
            AuthError.Unknown to SignInFormError.Unknown,
        ).forEach { (error, formError) ->
            authRepository.signInError = error

            google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

            assertEquals("$error", formError, state.formError)
            assertFalse("$error", state.isLoading)
            assertFalse("$error", state.isFinished)
        }
    }

    @Test
    fun google_whenOfflineModeCantBeTurnedOff_isAnUnknownFormErrorWithoutCallingTheBackend() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.writeError = StorageError.Unavailable

        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(SignInFormError.Unknown, state.formError)
        assertNull(authRepository.lastSignInCredential)
        assertFalse(state.isLoading)
    }

    @Test
    fun google_aResultWithoutARequestIsIgnored() {
        viewModel.onSignInResult(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertNull(authRepository.lastSignInCredential)
        assertFalse(state.isFinished)
    }

    @Test
    fun google_aFailureCanBeRetriedAndSucceeds() {
        google(SignInStepResult.NoAccount)

        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertNull(state.formError)
        assertTrue(state.isFinished)
    }

    @Test
    fun google_isIgnoredWhileLoading() {
        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onOfflineClick()

        assertEquals(SignInProvider.Google, state.requestedProvider)
        assertFalse(state.isFinished)
    }
}

private val SIGN_IN_CONFIG = SignInConfig(googleWebClientId = "web-client-id")
