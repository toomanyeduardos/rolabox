package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val EMAIL = "toomanyeduardos@gmail.com"

class ResetPasswordScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var email: String? = null
    private var sendClicks = 0
    private var resendClicks = 0
    private var backClicks = 0
    private var signInClicks = 0

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(state: ResetPasswordUiState = ResetPasswordUiState(), dark: Boolean = false) {
        composeRule.setContent {
            RolaboxTheme(darkTheme = dark) {
                ResetPasswordScreen(
                    state = state,
                    onEmailChange = { email = it },
                    onSendClick = { sendClicks++ },
                    onResendClick = { resendClicks++ },
                    onBackClick = { backClicks++ },
                    onSignInClick = { signInClicks++ },
                )
            }
        }
    }

    private fun sent(cooldown: Int = 0, error: ResetPasswordError? = null) = ResetPasswordUiState(
        step = ResetPasswordStep.Sent,
        email = EMAIL,
        sentTo = EMAIL,
        cooldownSeconds = cooldown,
        error = error,
    )

    @Test
    fun step1_showsEverythingFromTheDesign() {
        show(ResetPasswordUiState(email = EMAIL))

        composeRule.onNodeWithText(string(R.string.account_lcd_reset).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_lcd_step_1_of_2).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_reset_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_reset_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithTag(ResetPasswordTags.EMAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ResetPasswordTags.SEND).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_reset_footer_prompt)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_reset_footer_action)).assertIsDisplayed()
        composeRule.onAllNodesWithText("ERR").assertCountEquals(0)
    }

    @Test
    fun step1_showsThePrefilledEmail() {
        show(ResetPasswordUiState(email = EMAIL))

        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(ResetPasswordTags.EMAIL)))
            .assertTextEquals(string(R.string.account_field_email), EMAIL)
    }

    @Test
    fun step1_reportsTypingAndTheTaps() {
        show()

        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(ResetPasswordTags.EMAIL)))
            .performTextInput("a@b.co")
        composeRule.onNodeWithTag(ResetPasswordTags.SEND).performClick()
        composeRule.onNodeWithTag(ResetPasswordTags.SIGN_IN_LINK).performClick()

        assertEquals("a@b.co", email)
        assertEquals(1, sendClicks)
        assertEquals(1, signInClicks)
    }

    @Test
    fun step1_invalidEmailShowsTheFieldError() {
        show(ResetPasswordUiState(email = "nope", emailError = EmailError.Invalid))

        composeRule.onNodeWithText(string(R.string.account_error_email_invalid)).assertIsDisplayed()
    }

    @Test
    fun step1_noNetworkShowsTheErrorAndTheLcdTag() {
        show(ResetPasswordUiState(email = EMAIL, error = ResetPasswordError.Network))

        composeRule.onNodeWithText(string(R.string.account_reset_error_network)).assertIsDisplayed()
        composeRule.onNodeWithText("ERR").assertIsDisplayed()
    }

    @Test
    fun step1_tooManyRequestsShowsTheError() {
        show(ResetPasswordUiState(email = EMAIL, error = ResetPasswordError.TooManyRequests))

        composeRule.onNodeWithText(string(R.string.account_reset_error_too_many_requests)).assertIsDisplayed()
    }

    @Test
    fun step2_showsTheConfirmationWithTheSameCopyWhateverTheAccount() {
        show(sent(cooldown = 42))

        composeRule.onNodeWithText(string(R.string.account_lcd_step_2_of_2).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_reset_sent_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_reset_sent_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithTag(ResetPasswordTags.SENT_TO).assertTextContains(EMAIL, substring = true)
        composeRule.onNodeWithTag(ResetPasswordTags.BACK_TO_SIGN_IN).assertIsDisplayed()
    }

    @Test
    fun step2_duringTheCooldownTheResendKeyIsDisabledAndCountsDown() {
        show(sent(cooldown = 42))

        composeRule.onNodeWithTag(ResetPasswordTags.RESEND)
            .assertIsNotEnabled()
            .assertTextEquals(string(R.string.account_reset_resend_countdown, "0:42"))
        composeRule.onNodeWithTag(ResetPasswordTags.RESEND).performClick()
        assertEquals(0, resendClicks)
    }

    @Test
    fun step2_afterTheCooldownResendIsEnabledAndReportsTheTap() {
        show(sent())

        composeRule.onNodeWithTag(ResetPasswordTags.RESEND)
            .assertIsEnabled()
            .assertTextEquals(string(R.string.account_reset_resend))
            .performClick()

        assertEquals(1, resendClicks)
    }

    @Test
    fun step2_backToSignInReportsTheTap() {
        show(sent())

        composeRule.onNodeWithTag(ResetPasswordTags.BACK_TO_SIGN_IN).performClick()

        assertEquals(1, signInClicks)
    }

    @Test
    fun step2_offlineShowsTheErrorAndKeepsResendEnabled() {
        show(sent(error = ResetPasswordError.Network))

        composeRule.onNodeWithText(string(R.string.account_reset_error_network)).assertIsDisplayed()
        composeRule.onNodeWithText("ERR").assertIsDisplayed()
        composeRule.onNodeWithTag(ResetPasswordTags.RESEND).assertIsEnabled()
    }

    @Test
    fun step2_tooManyRequestsShowsTheErrorAndLocksResend() {
        show(sent(cooldown = 299, error = ResetPasswordError.TooManyRequests))

        composeRule.onNodeWithText(string(R.string.account_reset_error_too_many_requests)).assertIsDisplayed()
        composeRule.onNodeWithTag(ResetPasswordTags.RESEND)
            .assertIsNotEnabled()
            .assertTextEquals(string(R.string.account_reset_resend_countdown, "4:59"))
    }

    @Test
    fun bothStepsRenderInTheDarkTheme() {
        show(sent(cooldown = 10), dark = true)

        composeRule.onNodeWithTag(ResetPasswordTags.RESEND).assertIsDisplayed()
    }
}
