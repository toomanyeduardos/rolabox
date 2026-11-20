package com.eduardoflores.rolabox.auth.ui.impl

import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountError
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInMethod
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.auth.data.testing.FakeDeleteAccountUseCase
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInRequest
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepResult
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// ADR-037: the user re-authenticates with what their account has, and the account is deleted.
class DeleteAccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val deleteAccount = FakeDeleteAccountUseCase()
    private val config = SignInConfig(googleWebClientId = "web-client")

    @Test
    fun aPasswordAccount_isAskedForItsPassword() {
        val state = viewModel(SignInMethod.PASSWORD).uiState.value

        assertTrue(state.showsPassword)
        assertFalse(state.showsGoogle)
    }

    @Test
    fun aGoogleAccount_isOfferedGoogle() {
        val state = viewModel(SignInMethod.GOOGLE).uiState.value

        assertFalse(state.showsPassword)
        assertTrue(state.showsGoogle)
    }

    @Test
    fun aLinkedAccount_isOfferedBoth() {
        val state = viewModel(SignInMethod.PASSWORD, SignInMethod.GOOGLE).uiState.value

        assertTrue(state.showsPassword)
        assertTrue(state.showsGoogle)
    }

    // So the user is never left with no way to confirm.
    @Test
    fun anAccountWithNoKnownMethod_isOfferedBoth() {
        val state = viewModel().uiState.value

        assertTrue(state.showsPassword)
        assertTrue(state.showsGoogle)
    }

    @Test
    fun delete_withAPassword_deletesAndFinishes() {
        val viewModel = viewModel(SignInMethod.PASSWORD)
        viewModel.onPasswordChange("secret")

        viewModel.onDeleteClick()

        assertEquals("secret", deleteAccount.lastPassword)
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun delete_withNoPassword_asksForIt_andDeletesNothing() {
        val viewModel = viewModel(SignInMethod.PASSWORD)

        viewModel.onDeleteClick()

        assertEquals(DeleteAccountPasswordError.Required, viewModel.uiState.value.passwordError)
        assertNull(deleteAccount.lastPassword)
    }

    @Test
    fun aWrongPassword_isAboutThePassword() {
        deleteAccount.error = DeleteAccountError.Auth(AuthError.InvalidCredential)
        val viewModel = viewModel(SignInMethod.PASSWORD)
        viewModel.onPasswordChange("wrong")

        viewModel.onDeleteClick()

        val state = viewModel.uiState.value
        assertEquals(DeleteAccountPasswordError.Wrong, state.passwordError)
        assertFalse(state.isLoading)
        assertFalse(state.isFinished)
    }

    @Test
    fun offline_isNetwork() {
        assertEquals(DeleteAccountFormError.Network, formErrorFor(DeleteAccountError.Auth(AuthError.Network)))
        assertEquals(DeleteAccountFormError.Network, formErrorFor(DeleteAccountError.Remote(RemoteError.Unavailable)))
    }

    @Test
    fun requiresRecentLogin_asksToSignInAgain() {
        assertEquals(
            DeleteAccountFormError.SignInAgain,
            formErrorFor(DeleteAccountError.Auth(AuthError.RequiresRecentLogin)),
        )
    }

    @Test
    fun rejectedByTheBackend_isUnknown() {
        assertEquals(DeleteAccountFormError.Unknown, formErrorFor(DeleteAccountError.Remote(RemoteError.Rejected)))
    }

    @Test
    fun editingThePassword_clearsTheErrors() {
        deleteAccount.error = DeleteAccountError.Auth(AuthError.Network)
        val viewModel = viewModel(SignInMethod.PASSWORD)
        viewModel.onPasswordChange("secret")
        viewModel.onDeleteClick()

        viewModel.onPasswordChange("secret2")

        assertNull(viewModel.uiState.value.formError)
        assertNull(viewModel.uiState.value.passwordError)
    }

    @Test
    fun google_asksForThePicker_thenDeletesWithItsCredential() {
        val viewModel = viewModel(SignInMethod.GOOGLE)

        viewModel.onProviderClick(SignInProvider.Google)
        assertEquals(SignInRequest(SignInProvider.Google, config), viewModel.uiState.value.signInRequest)

        viewModel.onSignInResult(SignInStepResult.Credential(GOOGLE))

        assertEquals(GOOGLE, deleteAccount.lastCredential)
        assertTrue(viewModel.uiState.value.isFinished)
        assertNull(viewModel.uiState.value.signInRequest)
    }

    // With Google, an invalid credential is the picker giving another account (Firebase's user mismatch).
    @Test
    fun google_anotherAccount_saysSo() {
        deleteAccount.error = DeleteAccountError.Auth(AuthError.InvalidCredential)
        val viewModel = viewModel(SignInMethod.GOOGLE)

        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onSignInResult(SignInStepResult.Credential(GOOGLE))

        assertEquals(DeleteAccountFormError.OtherAccount, viewModel.uiState.value.formError)
    }

    @Test
    fun google_pickerClosed_deletesNothing_andStopsLoading() {
        val viewModel = viewModel(SignInMethod.GOOGLE)

        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onSignInResult(SignInStepResult.Cancelled)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(deleteAccount.lastCredential)
    }

    @Test
    fun google_noAccountOnTheDevice_saysSo() {
        val viewModel = viewModel(SignInMethod.GOOGLE)

        viewModel.onProviderClick(SignInProvider.Google)
        viewModel.onSignInResult(SignInStepResult.NoAccount)

        assertEquals(DeleteAccountFormError.NoAccount, viewModel.uiState.value.formError)
    }

    @Test
    fun aResultWithoutARequest_isIgnored() {
        val viewModel = viewModel(SignInMethod.GOOGLE)

        viewModel.onSignInResult(SignInStepResult.Credential(GOOGLE))

        assertNull(deleteAccount.lastCredential)
    }

    private fun formErrorFor(error: DeleteAccountError): DeleteAccountFormError? {
        deleteAccount.error = error
        val viewModel = viewModel(SignInMethod.PASSWORD)
        viewModel.onPasswordChange("secret")
        viewModel.onDeleteClick()
        return viewModel.uiState.value.formError
    }

    private fun viewModel(vararg methods: SignInMethod): DeleteAccountViewModelImpl {
        authRepository.setAuthState(
            AuthState.SignedIn(
                AuthUser(id = "1", displayName = "Ed", photoUrl = null, signInMethods = methods.toSet()),
            ),
        )
        return DeleteAccountViewModelImpl(authRepository, deleteAccount, config)
    }

    private companion object {
        val GOOGLE = SignInCredential.GoogleIdToken("token")
    }
}
