package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInButton
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepEffect
import com.eduardoflores.rolabox.common.designsystem.component.FieldError
import com.eduardoflores.rolabox.common.designsystem.component.FooterLink
import com.eduardoflores.rolabox.common.designsystem.component.OrDivider
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.PrimaryButton
import com.eduardoflores.rolabox.common.designsystem.component.RecessedField
import com.eduardoflores.rolabox.common.designsystem.component.TextAction
import com.eduardoflores.rolabox.common.designsystem.component.UnderlinedLink
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

/**
 * Connects [SignInScreen] to its ViewModel, which the entry creates (ADR-021). The exits are reported to
 * `:app` (ADR-012).
 */
@Composable
internal fun SignInRoute(
    viewModel: SignInViewModel,
    onCreateAccountClick: () -> Unit,
    onForgotPasswordClick: (email: String) -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) currentOnSignedIn()
    }
    SignInStepEffect(
        request = state.signInRequest,
        onResult = viewModel::onSignInResult,
    )
    SignInScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignInClick = viewModel::onSubmit,
        onForgotPasswordClick = { onForgotPasswordClick(state.email.trim()) },
        onProviderClick = viewModel::onProviderClick,
        onOfflineClick = viewModel::onOfflineClick,
        onCreateAccountClick = onCreateAccountClick,
        modifier = modifier,
    )
}

@Composable
internal fun SignInScreen(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onProviderClick: (SignInProvider) -> Unit,
    onOfflineClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lcdError = state.lcdErrorRes()
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(lcdError ?: R.string.account_lcd_sign_in),
        lcdRight = stringResource(R.string.account_lcd_offline_ok),
        lcdError = lcdError != null,
        title = stringResource(R.string.account_sign_in_title),
        subtitle = stringResource(R.string.account_sign_in_subtitle),
        footer = {
            UnderlinedLink(
                text = stringResource(R.string.account_sign_in_offline),
                onClick = onOfflineClick,
                modifier = Modifier.testTag(SignInTags.OFFLINE),
            )
            FooterLink(
                prompt = stringResource(R.string.account_sign_in_footer_prompt),
                action = stringResource(R.string.account_sign_in_footer_action),
                onClick = onCreateAccountClick,
            )
        },
    ) {
        SignInFields(
            state = state,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onSubmit = onSignInClick,
            onForgotPasswordClick = onForgotPasswordClick,
        )
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.formError?.let { FieldError(stringResource(it.messageRes())) }
            PrimaryButton(
                text = stringResource(R.string.account_sign_in_cta),
                onClick = onSignInClick,
                loading = state.isLoading,
                modifier = Modifier.testTag(SignInTags.SUBMIT),
            )
            OrDivider()
            SignInButton(
                provider = SignInProvider.Google,
                onClick = { onProviderClick(SignInProvider.Google) },
                modifier = Modifier.testTag(SignInTags.GOOGLE),
                enabled = !state.isLoading,
            )
        }
    }
}

@Composable
private fun SignInFields(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPasswordClick: () -> Unit,
) {
    val enabled = !state.isLoading
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RecessedField(
            label = stringResource(R.string.account_field_email),
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier.testTag(SignInTags.EMAIL),
            enabled = enabled,
            contentType = ContentType.EmailAddress,
            keyboardType = KeyboardType.Email,
            error = state.emailError?.let { stringResource(it.messageRes()) },
        )
        RecessedField(
            label = stringResource(R.string.account_field_password),
            value = state.password,
            onValueChange = onPasswordChange,
            modifier = Modifier.testTag(SignInTags.PASSWORD),
            enabled = enabled,
            password = true,
            contentType = ContentType.Password,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            error = state.passwordError?.let { stringResource(it.messageRes()) },
            labelAction = {
                TextAction(
                    text = stringResource(R.string.account_sign_in_forgot),
                    onClick = onForgotPasswordClick,
                    modifier = Modifier.testTag(SignInTags.FORGOT_PASSWORD),
                    compact = true,
                    reserveTouchTarget = false,
                )
            },
        )
    }
}

// The LCD says what went wrong in a few words, and the message says it in full.
private fun SignInUiState.lcdErrorRes(): Int? = when {
    passwordError == SignInPasswordError.BadLogin -> R.string.account_lcd_bad_login
    else -> formError?.lcdRes()
}

private fun SignInFormError.lcdRes(): Int = when (this) {
    SignInFormError.Network -> R.string.account_lcd_err_no_network
    SignInFormError.TooManyRequests -> R.string.account_lcd_err_try_later
    SignInFormError.AccountDisabled -> R.string.account_lcd_err_disabled
    SignInFormError.NoAccount -> R.string.account_lcd_err_no_account
    SignInFormError.Unknown -> R.string.account_lcd_err_unknown
}

private fun SignInFormError.messageRes(): Int = when (this) {
    SignInFormError.Network -> R.string.account_sign_in_error_network
    SignInFormError.TooManyRequests, SignInFormError.Unknown -> R.string.account_sign_in_error_try_later
    SignInFormError.AccountDisabled -> R.string.account_sign_in_error_disabled
    SignInFormError.NoAccount -> R.string.account_error_no_sign_in_account
}

private fun SignInPasswordError.messageRes(): Int = when (this) {
    SignInPasswordError.Required -> R.string.account_error_password_required
    SignInPasswordError.BadLogin -> R.string.account_sign_in_error_bad_login
}

private fun EmailError.messageRes(): Int = when (this) {
    EmailError.Required -> R.string.account_error_email_required
    EmailError.Invalid, EmailError.AlreadyInUse -> R.string.account_error_email_invalid
}

@PreviewLightDark
@Composable
private fun SignInScreenPreview() {
    RolaboxTheme {
        SignInScreen(
            state = SignInUiState(email = "toomanyeduardos@gmail.com", password = "secret"),
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

@PreviewLightDark
@Composable
private fun SignInScreenBadLoginPreview() {
    RolaboxTheme {
        SignInScreen(
            state = SignInUiState(
                email = "toomanyeduardos@gmail.com",
                password = "secret",
                passwordError = SignInPasswordError.BadLogin,
            ),
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
