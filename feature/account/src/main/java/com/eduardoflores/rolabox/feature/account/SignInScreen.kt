package com.eduardoflores.rolabox.feature.account

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.core.designsystem.component.FieldError
import com.eduardoflores.rolabox.core.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.core.designsystem.component.PrimaryButton
import com.eduardoflores.rolabox.core.designsystem.component.RecessedField
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

/** Connects [SignInScreen] to its ViewModel. The exits are reported to `:app` (ADR-012). */
@Composable
internal fun SignInRoute(
    onCreateAccountClick: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) currentOnSignedIn()
    }
    SignInScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignInClick = viewModel::onSubmit,
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
    onCreateAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lcdError = state.lcdErrorRes()
    val enabled = !state.isLoading
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(lcdError ?: R.string.account_lcd_sign_in),
        lcdError = lcdError != null,
        title = stringResource(R.string.account_sign_in_title),
        subtitle = stringResource(R.string.account_sign_in_subtitle),
        footer = {
            FooterLink(
                prompt = stringResource(R.string.account_sign_in_footer_prompt),
                action = stringResource(R.string.account_sign_in_footer_action),
                onClick = onCreateAccountClick,
            )
        },
    ) {
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
                onImeAction = onSignInClick,
                error = state.passwordError?.let { stringResource(it.messageRes()) },
            )
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.formError?.let { FieldError(stringResource(it.messageRes())) }
            PrimaryButton(
                text = stringResource(R.string.account_sign_in_cta),
                onClick = onSignInClick,
                loading = state.isLoading,
                modifier = Modifier.testTag(SignInTags.SUBMIT),
            )
        }
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
    SignInFormError.Unknown -> R.string.account_lcd_err_unknown
}

private fun SignInFormError.messageRes(): Int = when (this) {
    SignInFormError.Network -> R.string.account_sign_in_error_network
    SignInFormError.TooManyRequests, SignInFormError.Unknown -> R.string.account_sign_in_error_try_later
    SignInFormError.AccountDisabled -> R.string.account_sign_in_error_disabled
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
            onCreateAccountClick = {},
        )
    }
}
