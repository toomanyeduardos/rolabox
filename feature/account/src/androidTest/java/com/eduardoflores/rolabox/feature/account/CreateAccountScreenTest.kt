package com.eduardoflores.rolabox.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.eduardoflores.rolabox.core.auth.api.PasswordPolicy
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateAccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var name: String? = null
    private var email: String? = null
    private var password: String? = null
    private var createClicks = 0
    private var googleClicks = 0
    private var backClicks = 0
    private var signInClicks = 0

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(state: CreateAccountUiState = CreateAccountUiState(), dark: Boolean = false) {
        composeRule.setContent {
            RolaboxTheme(darkTheme = dark) {
                CreateAccountScreen(
                    state = state,
                    onNameChange = { name = it },
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onCreateClick = { createClicks++ },
                    onGoogleClick = { googleClicks++ },
                    onBack = { backClicks++ },
                    onSignInClick = { signInClicks++ },
                )
            }
        }
    }

    private fun stateWith(password: String) =
        CreateAccountUiState(password = password, strength = PasswordPolicy.strengthOf(password))

    private fun field(tag: String) = composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun empty_showsEverythingFromTheDesign() {
        show()

        composeRule.onNodeWithContentDescription(string(com.eduardoflores.rolabox.core.designsystem.R.string.ds_back))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_lcd_new_account).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_lcd_step).uppercase()).assertIsDisplayed()
        // The heading and the button.
        composeRule.onAllNodesWithText(string(R.string.account_create_title)).assertCountEquals(2)
        composeRule.onNodeWithText(string(R.string.account_create_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_field_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_field_email)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_field_password)).assertIsDisplayed()
        composeRule.onNodeWithTag(CreateAccountTags.SUBMIT).assertIsDisplayed()
        composeRule.onNodeWithText(
            string(com.eduardoflores.rolabox.core.designsystem.R.string.ds_or),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(CreateAccountTags.GOOGLE).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_create_footer_prompt)).assertIsDisplayed()
    }

    @Test
    fun empty_hasNoStrengthMeterAndNoErrors() {
        show()

        composeRule.onAllNodesWithText(string(R.string.account_strength_weak)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.account_error_name_required)).assertCountEquals(0)
    }

    @Test
    fun darkTheme_showsTheSameScreen() {
        show(dark = true)

        composeRule.onNodeWithTag(CreateAccountTags.SUBMIT).assertIsDisplayed()
    }

    @Test
    fun typing_reportsEachFieldsText() {
        var current by mutableStateOf(CreateAccountUiState())
        composeRule.setContent {
            RolaboxTheme {
                CreateAccountScreen(
                    state = current,
                    onNameChange = {
                        name = it
                        current = current.copy(name = it)
                    },
                    onEmailChange = {
                        email = it
                        current = current.copy(email = it)
                    },
                    onPasswordChange = {
                        password = it
                        current = current.copy(password = it)
                    },
                    onCreateClick = {},
                    onGoogleClick = {},
                    onBack = {},
                    onSignInClick = {},
                )
            }
        }

        field(CreateAccountTags.NAME).performTextInput("Alex")
        field(CreateAccountTags.EMAIL).performTextInput("alex@mail.com")
        field(CreateAccountTags.PASSWORD).performTextInput("secret")

        assertEquals("Alex", name)
        assertEquals("alex@mail.com", email)
        assertEquals("secret", password)
    }

    @Test
    fun fields_declareAutofillContentTypes() {
        show()

        fun hasContentType(type: ContentType) = SemanticsMatcher.expectValue(SemanticsProperties.ContentType, type)

        field(CreateAccountTags.NAME).assert(hasContentType(ContentType.PersonFullName))
        field(CreateAccountTags.EMAIL).assert(hasContentType(ContentType.EmailAddress))
        field(CreateAccountTags.PASSWORD).assert(hasContentType(ContentType.NewPassword))
    }

    @Test
    fun password_isHiddenUntilShown_andCanBeHiddenAgain() {
        show(stateWith("kdjfhqPwzm4x"))
        val hidden = "•".repeat("kdjfhqPwzm4x".length)

        field(CreateAccountTags.PASSWORD).assertTextEquals(string(R.string.account_field_password), hidden)

        composeRule.onNodeWithText(
            string(com.eduardoflores.rolabox.core.designsystem.R.string.ds_show_password),
        ).performClick()
        field(CreateAccountTags.PASSWORD).assertTextEquals(string(R.string.account_field_password), "kdjfhqPwzm4x")

        composeRule.onNodeWithText(
            string(com.eduardoflores.rolabox.core.designsystem.R.string.ds_hide_password),
        ).performClick()
        field(CreateAccountTags.PASSWORD).assertTextEquals(string(R.string.account_field_password), hidden)
    }

    @Test
    fun strengthMeter_showsTheLabelForEachStrength() {
        var current by mutableStateOf(stateWith("abc"))
        composeRule.setContent {
            RolaboxTheme {
                CreateAccountScreen(
                    state = current,
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onCreateClick = {},
                    onGoogleClick = {},
                    onBack = {},
                    onSignInClick = {},
                )
            }
        }
        val cases = mapOf(
            "abc" to R.string.account_strength_weak,
            "kdjfhqpwzmxn" to R.string.account_strength_fair,
            "kdjfhqPwzm4x" to R.string.account_strength_strong,
            "kdjfhqPwzm4x!" to R.string.account_strength_very_strong,
        )

        cases.forEach { (password, label) ->
            composeRule.runOnUiThread { current = stateWith(password) }
            composeRule.onNodeWithText(string(label)).assertIsDisplayed()
        }
    }

    @Test
    fun fieldErrors_areShownOnTheirFields() {
        show(
            CreateAccountUiState(
                nameError = NameError.Required,
                emailError = EmailError.Invalid,
                passwordError = PasswordError.TooShort,
            ),
        )

        composeRule.onNodeWithText(string(R.string.account_error_name_required)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_error_email_invalid)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_error_password_short, PasswordPolicy.MIN_LENGTH))
            .assertIsDisplayed()
    }

    @Test
    fun weakPasswordFromTheBackend_isShownOnThePasswordField() {
        show(CreateAccountUiState(passwordError = PasswordError.Rejected))

        composeRule.onNodeWithText(string(R.string.account_error_password_weak)).assertIsDisplayed()
    }

    @Test
    fun emailAlreadyInUse_offersSignInAndGoogle() {
        show(CreateAccountUiState(emailError = EmailError.AlreadyInUse))

        composeRule.onNodeWithText(string(R.string.account_error_email_in_use)).assertIsDisplayed()

        composeRule.onNodeWithTag(CreateAccountTags.EMAIL_IN_USE_SIGN_IN).performClick()
        composeRule.onNodeWithTag(CreateAccountTags.EMAIL_IN_USE_GOOGLE).performClick()

        assertEquals(1, signInClicks)
        assertEquals(1, googleClicks)
    }

    @Test
    fun otherEmailErrors_haveNoNudge() {
        show(CreateAccountUiState(emailError = EmailError.Invalid))

        // The footer's link is the only "Sign in".
        composeRule.onAllNodesWithText(string(R.string.account_email_in_use_sign_in)).assertCountEquals(1)
        composeRule.onAllNodesWithText(string(R.string.account_email_in_use_google)).assertCountEquals(0)
    }

    @Test
    fun noNetwork_isShownOnTheLcdAndAsAMessage() {
        show(CreateAccountUiState(formError = FormError.Network))

        composeRule.onNodeWithText(string(R.string.account_lcd_no_network).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText("ERR").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_error_network)).assertIsDisplayed()
    }

    @Test
    fun genericError_isShownAsAMessage() {
        show(CreateAccountUiState(formError = FormError.Generic))

        composeRule.onNodeWithText(string(R.string.account_error_generic)).assertIsDisplayed()
    }

    @Test
    fun loading_hidesTheButtonLabelAndIgnoresTaps() {
        show(CreateAccountUiState(isLoading = true))

        composeRule.onAllNodesWithText(string(R.string.account_create_cta)).assertCountEquals(1) // only the title
        composeRule.onNodeWithTag(CreateAccountTags.SUBMIT).performClick()

        assertEquals(0, createClicks)
    }

    @Test
    fun buttons_reportTheirClicks() {
        show()

        composeRule.onNodeWithTag(CreateAccountTags.SUBMIT).performClick()
        composeRule.onNodeWithTag(CreateAccountTags.GOOGLE).performClick()
        composeRule.onNodeWithContentDescription(string(com.eduardoflores.rolabox.core.designsystem.R.string.ds_back))
            .performClick()
        composeRule.onNodeWithText(string(R.string.account_create_footer_action)).performClick()

        assertEquals(1, createClicks)
        assertEquals(1, googleClicks)
        assertEquals(1, backClicks)
        assertEquals(1, signInClicks)
    }
}
