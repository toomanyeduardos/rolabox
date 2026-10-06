package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.auth.data.api.PasswordStrength
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInButton
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepEffect
import com.eduardoflores.rolabox.common.designsystem.component.FieldError
import com.eduardoflores.rolabox.common.designsystem.component.FooterLink
import com.eduardoflores.rolabox.common.designsystem.component.OrDivider
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.PrimaryButton
import com.eduardoflores.rolabox.common.designsystem.component.RecessedField
import com.eduardoflores.rolabox.common.designsystem.component.StrengthMeter
import com.eduardoflores.rolabox.common.designsystem.component.TextAction
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

/**
 * Connects [CreateAccountScreen] to its ViewModel, which the entry creates (ADR-021). The exits are reported to
 * `:app` (ADR-012).
 */
@Composable
internal fun CreateAccountRoute(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
    onSignedUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedUp by rememberUpdatedState(onSignedUp)
    LaunchedEffect(state.isSignedUp) {
        if (state.isSignedUp) currentOnSignedUp()
    }
    SignInStepEffect(
        request = state.signInRequest,
        onResult = viewModel::onSignInResult,
    )
    CreateAccountScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onCreateClick = viewModel::onSubmit,
        onProviderClick = viewModel::onProviderClick,
        onBack = onBack,
        onSignInClick = onSignInClick,
        modifier = modifier,
    )
}

@Composable
internal fun CreateAccountScreen(
    state: CreateAccountUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCreateClick: () -> Unit,
    onProviderClick: (SignInProvider) -> Unit,
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lcdError = state.formError?.lcdRes()
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(lcdError ?: R.string.account_lcd_new_account),
        lcdRight = if (lcdError != null) "" else stringResource(R.string.account_lcd_step),
        lcdError = lcdError != null,
        onBack = onBack,
        title = stringResource(R.string.account_create_title),
        subtitle = stringResource(R.string.account_create_subtitle),
        footer = {
            FooterLink(
                prompt = stringResource(R.string.account_create_footer_prompt),
                action = stringResource(R.string.account_create_footer_action),
                onClick = onSignInClick,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NameField(state, onNameChange)
            EmailField(state, onEmailChange, onSignInClick, onProviderClick)
            PasswordField(state, onPasswordChange, onCreateClick)
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.formError?.let { FieldError(stringResource(it.messageRes())) }
            PrimaryButton(
                text = stringResource(R.string.account_create_cta),
                onClick = onCreateClick,
                loading = state.isLoading,
                modifier = Modifier.testTag(CreateAccountTags.SUBMIT),
            )
            OrDivider()
            SignInButton(
                provider = SignInProvider.Google,
                onClick = { onProviderClick(SignInProvider.Google) },
                modifier = Modifier.testTag(CreateAccountTags.GOOGLE),
                enabled = !state.isLoading,
            )
        }
    }
}

@Composable
private fun NameField(state: CreateAccountUiState, onNameChange: (String) -> Unit) {
    RecessedField(
        label = stringResource(R.string.account_field_name),
        value = state.name,
        onValueChange = onNameChange,
        modifier = Modifier.testTag(CreateAccountTags.NAME),
        contentType = ContentType.PersonFullName,
        capitalization = KeyboardCapitalization.Words,
        error = state.nameError?.let { stringResource(R.string.account_error_name_required) },
    )
}

@Composable
private fun EmailField(
    state: CreateAccountUiState,
    onEmailChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onProviderClick: (SignInProvider) -> Unit,
) {
    RecessedField(
        label = stringResource(R.string.account_field_email),
        value = state.email,
        onValueChange = onEmailChange,
        modifier = Modifier.testTag(CreateAccountTags.EMAIL),
        contentType = ContentType.EmailAddress,
        keyboardType = KeyboardType.Email,
        error = state.emailError?.let { stringResource(it.messageRes()) },
        // With email enumeration protection on, Firebase can't say which way the existing account
        // signs in, so the user gets both ways. They wrap onto two lines when they don't fit on one.
        footer = if (state.emailError == EmailError.AlreadyInUse) {
            {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction(
                        text = stringResource(R.string.account_email_in_use_sign_in),
                        onClick = onSignInClick,
                        modifier = Modifier.testTag(CreateAccountTags.EMAIL_IN_USE_SIGN_IN),
                    )
                    TextAction(
                        text = stringResource(R.string.account_email_in_use_google),
                        onClick = { onProviderClick(SignInProvider.Google) },
                        modifier = Modifier.testTag(CreateAccountTags.EMAIL_IN_USE_GOOGLE),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun PasswordField(state: CreateAccountUiState, onPasswordChange: (String) -> Unit, onCreateClick: () -> Unit) {
    RecessedField(
        label = stringResource(R.string.account_field_password),
        value = state.password,
        onValueChange = onPasswordChange,
        modifier = Modifier.testTag(CreateAccountTags.PASSWORD),
        password = true,
        contentType = ContentType.NewPassword,
        imeAction = ImeAction.Done,
        onImeAction = onCreateClick,
        error = state.passwordError?.let { passwordErrorText(it) },
        footer = if (state.strength != PasswordStrength.Empty) {
            { StrengthMeter(level = state.strength.level) }
        } else {
            null
        },
    )
}

private fun EmailError.messageRes(): Int = when (this) {
    EmailError.Required -> R.string.account_error_email_required
    EmailError.Invalid -> R.string.account_error_email_invalid
    EmailError.AlreadyInUse -> R.string.account_error_email_in_use
}

// Only the errors that the LCD can say in a few words. A failed sign-up says it in the message alone.
private fun FormError.lcdRes(): Int? = when (this) {
    FormError.Network -> R.string.account_lcd_no_network
    FormError.NoAccount -> R.string.account_lcd_err_no_account
    FormError.SignInFailed -> R.string.account_lcd_err_unknown
    FormError.Generic -> null
}

private fun FormError.messageRes(): Int = when (this) {
    FormError.Network -> R.string.account_error_network
    FormError.NoAccount -> R.string.account_error_no_sign_in_account
    FormError.SignInFailed -> R.string.account_error_sign_in_failed
    FormError.Generic -> R.string.account_error_generic
}

@Composable
private fun passwordErrorText(error: PasswordError): String = when (error) {
    is PasswordError.TooShort -> stringResource(R.string.account_error_password_short, error.minLength)
    PasswordError.MissingCharacters -> stringResource(R.string.account_error_password_characters)
    PasswordError.Rejected -> stringResource(R.string.account_error_password_weak)
}

@PreviewLightDark
@Composable
private fun CreateAccountScreenPreview() {
    RolaboxTheme {
        CreateAccountScreen(
            state = CreateAccountUiState(
                name = "Eduardo Flores",
                email = "toomanyeduardos@gmail.com",
                password = "kdjfhqPwzm4x",
                strength = PasswordStrength.Good,
            ),
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onCreateClick = {},
            onProviderClick = {},
            onBack = {},
            onSignInClick = {},
        )
    }
}
