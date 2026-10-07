package com.eduardoflores.rolabox.auth.ui.impl

import arrow.core.Either
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignInUseCase
import com.eduardoflores.rolabox.auth.data.testing.FakeSignInUseCase
import com.eduardoflores.rolabox.auth.data.testing.SignInWithEmailRequest
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// The sign-in use case is a fake (ADR-021): what it does about offline mode (ADR-008, rule 7) is
// tested in :auth:data:impl. These tests cover what the screen does with each answer.
class SignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signIn = FakeSignInUseCase()
    private val userDataRepository = FakeUserDataRepository()
    private val viewModel = SignInViewModelImpl(signIn, userDataRepository, SIGN_IN_CONFIG)
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
        assertNull(signIn.lastEmailRequest)
        assertFalse(state.isLoading)
    }

    @Test
    fun submit_withMalformedEmailIsBlocked() {
        fill(email = "toomanyeduardos@gmail")

        viewModel.onSubmit()

        assertEquals(EmailError.Invalid, state.emailError)
        assertNull(state.passwordError)
        assertNull(signIn.lastEmailRequest)
    }

    @Test
    fun submit_withAnEmptyPasswordIsBlocked() {
        fill(password = "")

        viewModel.onSubmit()

        assertEquals(SignInPasswordError.Required, state.passwordError)
        assertNull(state.emailError)
        assertNull(signIn.lastEmailRequest)
    }

    @Test
    fun submit_doesNotApplyTheSignUpPasswordRules() {
        fill(password = "a")

        viewModel.onSubmit()

        assertNull(state.passwordError)
        assertEquals(SignInWithEmailRequest("toomanyeduardos@gmail.com", "a"), signIn.lastEmailRequest)
    }

    @Test
    fun submit_signsInWithATrimmedEmailAndTheUntouchedPassword() = runTest {
        fill(email = " toomanyeduardos@gmail.com ", password = " secret ")

        viewModel.onSubmit()

        assertEquals(
            SignInWithEmailRequest("toomanyeduardos@gmail.com", " secret "),
            signIn.lastEmailRequest,
        )
        assertTrue(state.isFinished)
        assertFalse(state.isLoading)
    }

    @Test
    fun submit_afterSigningInDoesNotSignInAgain() = runTest {
        fill()
        viewModel.onSubmit()
        viewModel.onEmailChange("other@mail.com")

        viewModel.onSubmit()

        assertEquals(SignInWithEmailRequest("toomanyeduardos@gmail.com", "secret"), signIn.lastEmailRequest)
    }

    @Test
    fun submit_whileLoadingDoesNotSubmitAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val slowSignIn = object : SignInUseCase by signIn {
            override suspend fun invoke(email: String, password: String): Either<SignInError, AuthUser> {
                calls++
                gate.await()
                return signIn(email, password)
            }
        }
        val slowViewModel = SignInViewModelImpl(slowSignIn, userDataRepository, SIGN_IN_CONFIG)
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
    fun submit_whenOfflineModeCantBeTurnedOff_isAFormError() {
        signIn.emailError = SignInError.Storage(StorageError.Unavailable)
        fill()

        viewModel.onSubmit()

        assertEquals(SignInFormError.Unknown, state.formError)
        assertNull(state.passwordError)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
    }

    @Test
    fun badCredentials_areOneGenericErrorOnThePasswordField() {
        signIn.emailError = SignInError.Auth(AuthError.InvalidCredential)
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
        signIn.emailError = SignInError.Auth(AuthError.InvalidEmail)
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
            signIn.emailError = SignInError.Auth(error)
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
        signIn.emailError = SignInError.Auth(AuthError.InvalidCredential)
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
        signIn.emailError = SignInError.Auth(AuthError.Network)
        fill()
        viewModel.onSubmit()

        viewModel.onPasswordChange("other")

        assertNull(state.formError)
    }

    @Test
    fun aFailedSignInCanBeRetriedAndSucceeds() {
        signIn.emailError = SignInError.Auth(AuthError.Network)
        fill()
        viewModel.onSubmit()

        signIn.emailError = null
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
    fun google_asksForThePickerWithTheConfigBeforeSigningIn() {
        viewModel.onProviderClick(SignInProvider.Google)

        assertEquals(SignInRequest(SignInProvider.Google, SIGN_IN_CONFIG), state.signInRequest)
        assertTrue(state.isLoading)
        assertNull(signIn.lastCredential)
    }

    @Test
    fun google_success_exchangesTheTokenAndFinishes() {
        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(SignInCredential.GoogleIdToken("id-token"), signIn.lastCredential)
        assertTrue(state.isFinished)
        assertFalse(state.isLoading)
        assertNull(state.signInRequest)
        assertNull(state.formError)
    }

    @Test
    fun google_cancel_returnsSilently() {
        google(SignInStepResult.Cancelled)

        assertNull(state.formError)
        assertNull(state.passwordError)
        assertNull(signIn.lastCredential)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
        assertNull(state.signInRequest)
    }

    @Test
    fun google_noAccountOnTheDevice_isAFormError() {
        google(SignInStepResult.NoAccount)

        assertEquals(SignInFormError.NoAccount, state.formError)
        assertNull(signIn.lastCredential)
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
            signIn.credentialError = SignInError.Auth(error)

            google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

            assertEquals("$error", formError, state.formError)
            assertFalse("$error", state.isLoading)
            assertFalse("$error", state.isFinished)
        }
    }

    @Test
    fun google_whenOfflineModeCantBeTurnedOff_isAnUnknownFormError() {
        signIn.credentialError = SignInError.Storage(StorageError.Unavailable)

        google(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertEquals(SignInFormError.Unknown, state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
    }

    @Test
    fun google_aResultWithoutARequestIsIgnored() {
        viewModel.onSignInResult(SignInStepResult.Credential(SignInCredential.GoogleIdToken("id-token")))

        assertNull(signIn.lastCredential)
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

        assertEquals(SignInProvider.Google, state.signInRequest?.provider)
        assertFalse(state.isFinished)
    }
}

private val SIGN_IN_CONFIG = SignInConfig(googleWebClientId = "web-client-id")
