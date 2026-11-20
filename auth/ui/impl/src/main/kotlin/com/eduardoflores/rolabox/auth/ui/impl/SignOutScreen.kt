package com.eduardoflores.rolabox.auth.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.FieldError
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.PrimaryButton
import com.eduardoflores.rolabox.common.designsystem.component.SecondaryButton
import com.eduardoflores.rolabox.common.designsystem.component.UnderlinedLink
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

/**
 * Connects [SignOutScreen] to its ViewModel, which the entry creates (ADR-021). The exits are
 * reported to `:app` (ADR-012).
 */
@Composable
internal fun SignOutRoute(
    viewModel: SignOutViewModel,
    onCancelClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) currentOnSignedOut()
    }
    SignOutScreen(
        state = state,
        onSignOutClick = viewModel::onSignOutClick,
        onCancelClick = onCancelClick,
        onDeleteAccountClick = onDeleteAccountClick,
        modifier = modifier,
    )
}

@Composable
internal fun SignOutScreen(
    state: SignOutUiState,
    onSignOutClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(R.string.account_lcd_sign_out),
        lcdError = state.hasError,
        onBack = onCancelClick,
        title = stringResource(R.string.account_sign_out_title),
        subtitle = stringResource(R.string.account_sign_out_subtitle),
        // Deleting the account is the screen's quiet way out: it opens a screen of its own that confirms it (ADR-037).
        footer = {
            UnderlinedLink(
                text = stringResource(R.string.account_sign_out_delete_account),
                onClick = onDeleteAccountClick,
                modifier = Modifier.testTag(SignOutTags.DELETE_ACCOUNT),
            )
        },
    ) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = stringResource(R.string.account_sign_out_cta),
                onClick = onSignOutClick,
                loading = state.isLoading,
                modifier = Modifier.testTag(SignOutTags.SIGN_OUT),
            )
            SecondaryButton(
                text = stringResource(R.string.account_sign_out_cancel),
                onClick = onCancelClick,
                enabled = !state.isLoading,
                modifier = Modifier.testTag(SignOutTags.CANCEL),
            )
            if (state.hasError) FieldError(stringResource(R.string.account_sign_out_error))
        }
    }
}

@PreviewLightDark
@Composable
private fun SignOutPreview() {
    RolaboxTheme {
        SignOutScreen(state = SignOutUiState(), onSignOutClick = {}, onCancelClick = {}, onDeleteAccountClick = {})
    }
}

@PreviewLightDark
@Composable
private fun SignOutErrorPreview() {
    RolaboxTheme {
        SignOutScreen(
            state = SignOutUiState(hasError = true),
            onSignOutClick = {},
            onCancelClick = {},
            onDeleteAccountClick = {},
        )
    }
}
