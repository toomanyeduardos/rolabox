package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var email: String? = null
    private var password: String? = null
    private var signInClicks = 0
    private var forgotClicks = 0
    private var googleClicks = 0
    private var offlineClicks = 0
    private var createAccountClicks = 0

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(state: SignInUiState = SignInUiState(), dark: Boolean = false) {
        composeRule.setContent {
            RolaboxTheme(darkTheme = dark) {
                SignInScreen(
                    state = state,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onSignInClick = { signInClicks++ },
                    onForgotPasswordClick = { forgotClicks++ },
                    onProviderClick = { googleClicks++ },
                    onOfflineClick = { offlineClicks++ },
                    onCreateAccountClick = { createAccountClicks++ },
                )
            }
        }
    }

    private fun field(tag: String) = composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun idle_showsEverythingFromTheDesign() {
        show()

        composeRule.onNodeWithText(string(R.string.account_lcd_sign_in).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_lcd_offline_ok).uppercase()).assertIsDisplayed()
        // The heading and the button.
        composeRule.onAllNodesWithText(string(R.string.account_sign_in_title)).assertCountEquals(2)
        composeRule.onNodeWithText(string(R.string.account_sign_in_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_field_email)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_field_password)).assertIsDisplayed()
        composeRule.onNodeWithText(string(com.eduardoflores.rolabox.common.designsystem.R.string.ds_show_password))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SignInTags.FORGOT_PASSWORD).assertIsDisplayed()
        composeRule.onNodeWithTag(SignInTags.SUBMIT).assertIsDisplayed()
        composeRule.onNodeWithText(string(com.eduardoflores.rolabox.common.designsystem.R.string.ds_or))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SignInTags.GOOGLE).assertIsDisplayed()
        composeRule.onNodeWithTag(SignInTags.OFFLINE).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_sign_in_footer_prompt)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_sign_in_footer_action)).assertIsDisplayed()
    }

    @Test
    fun idle_hasNoErrors() {
        show()

        composeRule.onAllNodesWithText("ERR").assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.account_error_email_required)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.account_error_password_required)).assertCountEquals(0)
    }

    @Test
    fun idle_inDarkTheme_showsTheSameContent() {
        show(dark = true)

        composeRule.onNodeWithTag(SignInTags.SUBMIT).assertIsDisplayed()
        composeRule.onNodeWithTag(SignInTags.GOOGLE).assertIsDisplayed()
    }

    @Test
    fun typing_reportsEachFieldsValue() {
        var current by mutableStateOf(SignInUiState())
        composeRule.setContent {
            RolaboxTheme {
                SignInScreen(
                    state = current,
                    onEmailChange = {
                        email = it
                        current = current.copy(email = it)
                    },
                    onPasswordChange = {
                        password = it
                        current = current.copy(password = it)
                    },
                    onSignInClick = {},
                    onForgotPasswordClick = {},
                    onProviderClick = {},
                    onOfflineClick = {},
                    onCreateAccountClick = {},
                )
            }
        }

        field(SignInTags.EMAIL).performTextInput("a@b.co")
        field(SignInTags.PASSWORD).performTextInput("secret")

        assertEquals("a@b.co", email)
        assertEquals("secret", password)
    }

    @Test
    fun passwordIsHiddenUntilShown() {
        show(SignInUiState(password = "secret"))
        val hidden = "•".repeat("secret".length)
        val label = string(R.string.account_field_password)

        field(SignInTags.PASSWORD).assertTextEquals(label, hidden)
        composeRule.onNodeWithText(string(com.eduardoflores.rolabox.common.designsystem.R.string.ds_show_password))
            .performClick()
        field(SignInTags.PASSWORD).assertTextEquals(label, "secret")
        composeRule.onNodeWithText(string(com.eduardoflores.rolabox.common.designsystem.R.string.ds_hide_password))
            .performClick()
        field(SignInTags.PASSWORD).assertTextEquals(label, hidden)
    }

    @Test
    fun imeDoneOnThePasswordSubmits() {
        show()

        field(SignInTags.PASSWORD).performImeAction()

        assertEquals(1, signInClicks)
    }

    @Test
    fun fieldErrors_areShownOnTheirFields() {
        show(SignInUiState(emailError = EmailError.Invalid, passwordError = SignInPasswordError.Required))

        composeRule.onNodeWithText(string(R.string.account_error_email_invalid)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_error_password_required)).assertIsDisplayed()
    }

    @Test
    fun badLogin_isShownOnTheLcdAndOnThePassword() {
        show(SignInUiState(passwordError = SignInPasswordError.BadLogin))

        composeRule.onNodeWithText(string(R.string.account_lcd_bad_login)).assertIsDisplayed()
        composeRule.onNodeWithText("ERR").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_sign_in_error_bad_login)).assertIsDisplayed()
    }

    @Test
    fun formErrors_areShownOnTheLcdAndAsAMessage() {
        val cases = listOf(
            SignInFormError.Network to (R.string.account_lcd_err_no_network to R.string.account_sign_in_error_network),
            SignInFormError.TooManyRequests to
                (R.string.account_lcd_err_try_later to R.string.account_sign_in_error_try_later),
            SignInFormError.AccountDisabled to
                (R.string.account_lcd_err_disabled to R.string.account_sign_in_error_disabled),
            SignInFormError.NoAccount to
                (R.string.account_lcd_err_no_account to R.string.account_error_no_sign_in_account),
            SignInFormError.Unknown to (R.string.account_lcd_err_unknown to R.string.account_sign_in_error_try_later),
        )
        var current by mutableStateOf(SignInUiState())
        composeRule.setContent {
            RolaboxTheme {
                SignInScreen(
                    state = current,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSignInClick = {},
                    onForgotPasswordClick = {},
                    onProviderClick = {},
                    onOfflineClick = {},
                    onCreateAccountClick = {},
                )
            }
        }
        cases.forEach { (error, strings) ->
            val (lcd, message) = strings
            composeRule.runOnUiThread { current = SignInUiState(formError = error) }
            composeRule.onNodeWithText(string(lcd)).assertIsDisplayed()
            composeRule.onNodeWithText(string(message)).assertIsDisplayed()
        }
    }

    @Test
    fun loading_hidesTheButtonLabelAndIgnoresTaps() {
        show(SignInUiState(isLoading = true))

        composeRule.onAllNodesWithText(string(R.string.account_sign_in_cta)).assertCountEquals(1) // only the title
        composeRule.onNodeWithTag(SignInTags.SUBMIT).performClick()

        assertEquals(0, signInClicks)
    }

    @Test
    fun links_reportTheirClicks() {
        show()

        composeRule.onNodeWithTag(SignInTags.SUBMIT).performClick()
        composeRule.onNodeWithTag(SignInTags.FORGOT_PASSWORD).performClick()
        composeRule.onNodeWithTag(SignInTags.GOOGLE).performClick()
        composeRule.onNodeWithTag(SignInTags.OFFLINE).performClick()
        composeRule.onNodeWithText(string(R.string.account_sign_in_footer_action)).performClick()

        assertEquals(1, signInClicks)
        assertEquals(1, forgotClicks)
        assertEquals(1, googleClicks)
        assertEquals(1, offlineClicks)
        assertEquals(1, createAccountClicks)
    }

    @Test
    fun loading_disablesTheGoogleButton() {
        show(SignInUiState(isLoading = true))

        composeRule.onNodeWithTag(SignInTags.GOOGLE).performClick()

        assertEquals(0, googleClicks)
    }
}
