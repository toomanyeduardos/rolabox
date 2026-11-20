package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInButton
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInProvider
import com.eduardoflores.rolabox.auth.ui.impl.signin.SignInStepEffect
import com.eduardoflores.rolabox.common.designsystem.component.DestructiveButton
import com.eduardoflores.rolabox.common.designsystem.component.FieldError
import com.eduardoflores.rolabox.common.designsystem.component.OrDivider
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.RecessedField
import com.eduardoflores.rolabox.common.designsystem.component.SecondaryButton
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

/**
 * Connects [DeleteAccountScreen] to its ViewModel, which the entry creates (ADR-021). The exits are reported to
 * `:app` (ADR-012).
 */
@Composable
internal fun DeleteAccountRoute(
    viewModel: DeleteAccountViewModel,
    onCancelClick: () -> Unit,
    onDeletedAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDeletedAccount by rememberUpdatedState(onDeletedAccount)
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) currentOnDeletedAccount()
    }
    SignInStepEffect(request = state.signInRequest, onResult = viewModel::onSignInResult)
    DeleteAccountScreen(
        state = state,
        onPasswordChange = viewModel::onPasswordChange,
        onDeleteClick = viewModel::onDeleteClick,
        onProviderClick = viewModel::onProviderClick,
        onCancelClick = onCancelClick,
        modifier = modifier,
    )
}

/**
 * Says what is deleted, including everything on the phone, and asks the user to re-authenticate with what their
 * account has (ADR-037). With a password, the red key deletes with it. With Google only, the red key opens Google's
 * account picker. A linked account gets both.
 */
@Composable
internal fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onPasswordChange: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onProviderClick: (SignInProvider) -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val styles = RolaboxType.styles
    val lcdError = state.lcdErrorRes()
    val enabled = !state.isLoading
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(lcdError ?: R.string.account_lcd_delete_account),
        lcdError = lcdError != null,
        onBack = onCancelClick,
        title = stringResource(R.string.account_delete_title),
        subtitle = stringResource(R.string.account_delete_subtitle),
        footer = {},
    ) {
        Text(text = stringResource(R.string.account_delete_stays), style = styles.screenSubtitle)
        Text(text = stringResource(state.confirmRes()), style = styles.screenSubtitle)
        if (state.showsPassword) {
            RecessedField(
                label = stringResource(R.string.account_field_password),
                value = state.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.testTag(DeleteAccountTags.PASSWORD),
                enabled = enabled,
                password = true,
                contentType = ContentType.Password,
                imeAction = ImeAction.Done,
                onImeAction = onDeleteClick,
                error = state.passwordError?.let { stringResource(it.messageRes()) },
            )
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.formError?.let { FieldError(stringResource(it.messageRes())) }
            DestructiveButton(
                text = stringResource(R.string.account_delete_cta),
                onClick = { if (state.showsPassword) onDeleteClick() else onProviderClick(SignInProvider.Google) },
                loading = state.isLoading,
                modifier = Modifier.testTag(DeleteAccountTags.DELETE),
            )
            if (state.showsPassword && state.showsGoogle) {
                OrDivider()
                SignInButton(
                    provider = SignInProvider.Google,
                    onClick = { onProviderClick(SignInProvider.Google) },
                    modifier = Modifier.testTag(DeleteAccountTags.GOOGLE),
                    enabled = enabled,
                )
            }
            SecondaryButton(
                text = stringResource(R.string.account_delete_cancel),
                onClick = onCancelClick,
                enabled = enabled,
                modifier = Modifier.testTag(DeleteAccountTags.CANCEL),
            )
        }
    }
}

// Names only the ways to re-authenticate that the screen offers.
private fun DeleteAccountUiState.confirmRes(): Int = when {
    showsPassword && showsGoogle -> R.string.account_delete_confirm_either
    showsGoogle -> R.string.account_delete_confirm_google
    else -> R.string.account_delete_confirm_password
}

// The LCD says what went wrong in a few words, and the message says it in full.
private fun DeleteAccountUiState.lcdErrorRes(): Int? = when {
    passwordError == DeleteAccountPasswordError.Wrong -> R.string.account_lcd_err_wrong_password
    else -> formError?.lcdRes()
}

private fun DeleteAccountFormError.lcdRes(): Int = when (this) {
    DeleteAccountFormError.Network -> R.string.account_lcd_err_no_network
    DeleteAccountFormError.SignInAgain -> R.string.account_lcd_err_sign_in_again
    DeleteAccountFormError.OtherAccount -> R.string.account_lcd_err_sign_in_again
    DeleteAccountFormError.NoAccount -> R.string.account_lcd_err_no_account
    DeleteAccountFormError.Unknown -> R.string.account_lcd_err_unknown
}

private fun DeleteAccountFormError.messageRes(): Int = when (this) {
    DeleteAccountFormError.Network -> R.string.account_delete_error_network
    DeleteAccountFormError.SignInAgain -> R.string.account_delete_error_sign_in_again
    DeleteAccountFormError.OtherAccount -> R.string.account_delete_error_other_account
    DeleteAccountFormError.NoAccount -> R.string.account_error_no_sign_in_account
    DeleteAccountFormError.Unknown -> R.string.account_delete_error_unknown
}

private fun DeleteAccountPasswordError.messageRes(): Int = when (this) {
    DeleteAccountPasswordError.Required -> R.string.account_delete_error_password_required
    DeleteAccountPasswordError.Wrong -> R.string.account_delete_error_wrong_password
}

@PreviewLightDark
@Composable
private fun DeleteAccountPasswordPreview() {
    RolaboxTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(showsPassword = true, password = "secret"),
            onPasswordChange = {},
            onDeleteClick = {},
            onProviderClick = {},
            onCancelClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteAccountGooglePreview() {
    RolaboxTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(showsGoogle = true),
            onPasswordChange = {},
            onDeleteClick = {},
            onProviderClick = {},
            onCancelClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteAccountLinkedPreview() {
    RolaboxTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(showsPassword = true, showsGoogle = true),
            onPasswordChange = {},
            onDeleteClick = {},
            onProviderClick = {},
            onCancelClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteAccountWrongPasswordPreview() {
    RolaboxTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(
                showsPassword = true,
                password = "secret",
                passwordError = DeleteAccountPasswordError.Wrong,
            ),
            onPasswordChange = {},
            onDeleteClick = {},
            onProviderClick = {},
            onCancelClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteAccountNetworkErrorPreview() {
    RolaboxTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(
                showsPassword = true,
                password = "secret",
                formError = DeleteAccountFormError.Network,
            ),
            onPasswordChange = {},
            onDeleteClick = {},
            onProviderClick = {},
            onCancelClick = {},
        )
    }
}
