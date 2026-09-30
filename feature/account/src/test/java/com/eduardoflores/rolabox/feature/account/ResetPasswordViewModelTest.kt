package com.eduardoflores.rolabox.feature.account

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val EMAIL = "toomanyeduardos@gmail.com"
private const val SECOND = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val viewModel = ResetPasswordViewModel(authRepository)
    private val state get() = viewModel.uiState.value

    private fun sendTo(email: String = EMAIL) {
        viewModel.onEmailChange(email)
        viewModel.onSend()
    }

    @Test
    fun startsOnStep1WithNothingFilledIn() {
        assertEquals(ResetPasswordUiState(), state)
    }

    @Test
    fun prefill_setsTheEmailOnce() {
        viewModel.prefill(EMAIL)
        viewModel.onEmailChange("other@mail.com")
        viewModel.prefill(EMAIL)

        assertEquals("other@mail.com", state.email)
    }

    @Test
    fun send_withNoEmailIsBlockedAndDoesNotCallTheBackend() {
        viewModel.onSend()

        assertEquals(EmailError.Required, state.emailError)
        assertNull(authRepository.lastPasswordResetEmail)
        assertEquals(ResetPasswordStep.Email, state.step)
    }

    @Test
    fun send_withMalformedEmailIsBlocked() {
        sendTo("toomanyeduardos@gmail")

        assertEquals(EmailError.Invalid, state.emailError)
        assertNull(authRepository.lastPasswordResetEmail)
    }

    @Test
    fun editingTheEmailClearsItsError() {
        sendTo("nope")
        viewModel.onEmailChange("nope@")

        assertNull(state.emailError)
    }

    @Test
    fun send_trimsTheEmailAndMovesToStep2WithTheCooldownRunning() {
        sendTo("  $EMAIL ")

        assertEquals(EMAIL, authRepository.lastPasswordResetEmail)
        assertEquals(ResetPasswordStep.Sent, state.step)
        assertEquals(EMAIL, state.sentTo)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.cooldownSeconds)
        assertFalse(state.canResend)
        assertFalse(state.isLoading)
    }

    // The screen must say the same thing whether or not the account exists, so there is nothing to
    // tell them apart by: the repository reports success for both.
    @Test
    fun send_showsTheConfirmationWheneverTheRepositorySucceeds() {
        sendTo()

        assertEquals(ResetPasswordStep.Sent, state.step)
        assertNull(state.error)
    }

    @Test
    fun cooldown_countsDownEachSecondAndThenEnablesResend() = runTest {
        sendTo()

        advanceTimeBy(SECOND * 18 + 1)
        assertEquals(RESEND_COOLDOWN_SECONDS - 18, state.cooldownSeconds)
        assertFalse(state.canResend)

        advanceTimeBy(SECOND * (RESEND_COOLDOWN_SECONDS - 18))
        assertEquals(0, state.cooldownSeconds)
        assertTrue(state.canResend)
    }

    @Test
    fun resend_isIgnoredWhileTheCooldownRuns() {
        sendTo()

        viewModel.onResend()

        assertEquals(1, authRepository.passwordResetEmailCount)
    }

    @Test
    fun resend_afterTheCooldownSendsAgainAndRestartsIt() = runTest {
        sendTo()
        advanceTimeBy(SECOND * RESEND_COOLDOWN_SECONDS + 1)

        viewModel.onResend()

        assertEquals(2, authRepository.passwordResetEmailCount)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.cooldownSeconds)
        assertFalse(state.canResend)
    }

    @Test
    fun resend_offlineKeepsTheKeyEnabledSoTheUserCanRetry() = runTest {
        sendTo()
        advanceTimeBy(SECOND * RESEND_COOLDOWN_SECONDS + 1)
        authRepository.sendPasswordResetError = AuthError.Network

        viewModel.onResend()

        assertEquals(ResetPasswordError.Network, state.error)
        assertEquals(0, state.cooldownSeconds)
        assertTrue(state.canResend)
        assertEquals(ResetPasswordStep.Sent, state.step)
    }

    @Test
    fun resend_tooManyRequestsLocksTheKeyWithTheLongerCountdown() = runTest {
        sendTo()
        advanceTimeBy(SECOND * RESEND_COOLDOWN_SECONDS + 1)
        authRepository.sendPasswordResetError = AuthError.TooManyRequests

        viewModel.onResend()

        assertEquals(ResetPasswordError.TooManyRequests, state.error)
        assertEquals(TOO_MANY_REQUESTS_COOLDOWN_SECONDS, state.cooldownSeconds)
        assertFalse(state.canResend)
    }

    @Test
    fun resend_successClearsThePreviousError() = runTest {
        sendTo()
        advanceTimeBy(SECOND * RESEND_COOLDOWN_SECONDS + 1)
        authRepository.sendPasswordResetError = AuthError.Network
        viewModel.onResend()
        authRepository.sendPasswordResetError = null

        viewModel.onResend()

        assertNull(state.error)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.cooldownSeconds)
    }

    @Test
    fun send_offlineStaysOnStep1WithTheError() {
        authRepository.sendPasswordResetError = AuthError.Network

        sendTo()

        assertEquals(ResetPasswordStep.Email, state.step)
        assertEquals(ResetPasswordError.Network, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun send_tooManyRequestsStaysOnStep1WithTheError() {
        authRepository.sendPasswordResetError = AuthError.TooManyRequests

        sendTo()

        assertEquals(ResetPasswordStep.Email, state.step)
        assertEquals(ResetPasswordError.TooManyRequests, state.error)
    }

    @Test
    fun send_backendCallingTheEmailInvalidIsAFieldError() {
        authRepository.sendPasswordResetError = AuthError.InvalidEmail

        sendTo()

        assertEquals(EmailError.Invalid, state.emailError)
        assertNull(state.error)
    }

    @Test
    fun send_anErrorWeHaveNoCaseForIsUnknown() {
        authRepository.sendPasswordResetError = AuthError.Unknown

        sendTo()

        assertEquals(ResetPasswordError.Unknown, state.error)
    }

    @Test
    fun editEmail_returnsToStep1AndKeepsTheCooldown() {
        sendTo()

        viewModel.onEditEmail()

        assertEquals(ResetPasswordStep.Email, state.step)
        assertEquals(EMAIL, state.email)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.cooldownSeconds)
    }

    @Test
    fun sendingToTheSameEmailAgainDuringTheCooldownDoesNotSkipIt() {
        sendTo()
        viewModel.onEditEmail()

        viewModel.onSend()

        assertEquals(1, authRepository.passwordResetEmailCount)
        assertEquals(ResetPasswordStep.Sent, state.step)
    }

    @Test
    fun sendingToACorrectedEmailSendsAndRestartsTheCooldown() = runTest {
        sendTo("typo@mail.com")
        advanceTimeBy(SECOND * 10)
        viewModel.onEditEmail()

        sendTo()

        assertEquals(2, authRepository.passwordResetEmailCount)
        assertEquals(EMAIL, authRepository.lastPasswordResetEmail)
        assertEquals(EMAIL, state.sentTo)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.cooldownSeconds)
    }
}
