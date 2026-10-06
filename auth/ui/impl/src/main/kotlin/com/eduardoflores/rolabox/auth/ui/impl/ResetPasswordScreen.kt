package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.FieldError
import com.eduardoflores.rolabox.common.designsystem.component.FooterLink
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.PrimaryButton
import com.eduardoflores.rolabox.common.designsystem.component.ReadOnlyField
import com.eduardoflores.rolabox.common.designsystem.component.RecessedField
import com.eduardoflores.rolabox.common.designsystem.component.SecondaryButton
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

/**
 * Connects [ResetPasswordScreen] to its ViewModel, which the entry creates (ADR-021). The exits are reported to
 * `:app` (ADR-012).
 */
@Composable
internal fun ResetPasswordRoute(
    viewModel: ResetPasswordViewModel,
    email: String,
    onBackClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(email) { viewModel.prefill(email) }
    ResetPasswordScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onSendClick = viewModel::onSend,
        onResendClick = viewModel::onResend,
        onBackClick = if (state.step == ResetPasswordStep.Sent) viewModel::onEditEmail else onBackClick,
        onSignInClick = onSignInClick,
        modifier = modifier,
    )
}

@Composable
internal fun ResetPasswordScreen(
    state: ResetPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onResendClick: () -> Unit,
    onBackClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sent = state.step == ResetPasswordStep.Sent
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(R.string.account_lcd_reset),
        lcdRight = stringResource(if (sent) R.string.account_lcd_step_2_of_2 else R.string.account_lcd_step_1_of_2),
        lcdError = state.error != null,
        onBack = onBackClick,
        title = stringResource(if (sent) R.string.account_reset_sent_title else R.string.account_reset_title),
        subtitle = stringResource(if (sent) R.string.account_reset_sent_subtitle else R.string.account_reset_subtitle),
        footer = {
            if (!sent) {
                FooterLink(
                    prompt = stringResource(R.string.account_reset_footer_prompt),
                    action = stringResource(R.string.account_reset_footer_action),
                    onClick = onSignInClick,
                    modifier = Modifier.testTag(ResetPasswordTags.SIGN_IN_LINK),
                )
            }
        },
    ) {
        if (sent) {
            SentContent(state, onResendClick, onSignInClick)
        } else {
            EmailContent(state, onEmailChange, onSendClick)
        }
    }
}

@Composable
private fun EmailContent(state: ResetPasswordUiState, onEmailChange: (String) -> Unit, onSendClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AUTH_CONTENT_SPACING)) {
        RecessedField(
            label = stringResource(R.string.account_field_email),
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier.testTag(ResetPasswordTags.EMAIL),
            enabled = !state.isLoading,
            contentType = ContentType.EmailAddress,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            onImeAction = onSendClick,
            error = state.emailError?.let { stringResource(it.messageRes()) },
        )
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { FieldError(stringResource(it.messageRes())) }
            PrimaryButton(
                text = stringResource(R.string.account_reset_cta),
                onClick = onSendClick,
                loading = state.isLoading,
                modifier = Modifier.testTag(ResetPasswordTags.SEND),
            )
        }
    }
}

@Composable
private fun SentContent(state: ResetPasswordUiState, onResendClick: () -> Unit, onSignInClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AUTH_CONTENT_SPACING)) {
        ReadOnlyField(
            label = stringResource(R.string.account_reset_sent_to),
            value = state.sentTo,
            modifier = Modifier.testTag(ResetPasswordTags.SENT_TO),
        )
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = stringResource(R.string.account_reset_back_to_sign_in),
                onClick = onSignInClick,
                modifier = Modifier.testTag(ResetPasswordTags.BACK_TO_SIGN_IN),
            )
            SecondaryButton(
                text = if (state.cooldownSeconds > 0) {
                    stringResource(R.string.account_reset_resend_countdown, formatCountdown(state.cooldownSeconds))
                } else {
                    stringResource(R.string.account_reset_resend)
                },
                onClick = onResendClick,
                enabled = state.canResend,
                modifier = Modifier.testTag(ResetPasswordTags.RESEND),
            )
            state.error?.let { FieldError(stringResource(it.messageRes())) }
        }
    }
}

private fun ResetPasswordError.messageRes(): Int = when (this) {
    ResetPasswordError.Network -> R.string.account_reset_error_network
    ResetPasswordError.TooManyRequests -> R.string.account_reset_error_too_many_requests
    ResetPasswordError.Unknown -> R.string.account_reset_error_unknown
}

private fun EmailError.messageRes(): Int = when (this) {
    EmailError.Required -> R.string.account_error_email_required
    EmailError.Invalid, EmailError.AlreadyInUse -> R.string.account_error_email_invalid
}

private val AUTH_CONTENT_SPACING = 18.dp

private const val PREVIEW_EMAIL = "toomanyeduardos@gmail.com"

@PreviewLightDark
@Composable
private fun ResetPasswordStep1Preview() {
    RolaboxTheme {
        ResetPasswordScreen(
            state = ResetPasswordUiState(email = PREVIEW_EMAIL),
            onEmailChange = {},
            onSendClick = {},
            onResendClick = {},
            onBackClick = {},
            onSignInClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ResetPasswordStep2Preview() {
    RolaboxTheme {
        ResetPasswordScreen(
            state = ResetPasswordUiState(
                step = ResetPasswordStep.Sent,
                email = PREVIEW_EMAIL,
                sentTo = PREVIEW_EMAIL,
                cooldownSeconds = 42,
            ),
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ResetPasswordStep2TooManyRequestsPreview() {
    RolaboxTheme {
        ResetPasswordScreen(
            state = ResetPasswordUiState(
                step = ResetPasswordStep.Sent,
                email = PREVIEW_EMAIL,
                sentTo = PREVIEW_EMAIL,
                cooldownSeconds = 299,
                error = ResetPasswordError.TooManyRequests,
            ),
            {},
            {},
            {},
            {},
            {},
        )
    }
}
