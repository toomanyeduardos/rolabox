package com.eduardoflores.rolabox.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.core.designsystem.component.PrimaryButton
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

// Stands in for the Sign in screen so Create account has somewhere to come back to. The Sign in
// ticket replaces it.
@Composable
internal fun SignInPlaceholderScreen(onCreateAccountClick: () -> Unit, modifier: Modifier = Modifier) {
    AuthScaffold(
        modifier = modifier,
        lcdLeft = stringResource(R.string.account_sign_in_placeholder_title),
        title = stringResource(R.string.account_sign_in_placeholder_title),
        subtitle = stringResource(R.string.account_sign_in_placeholder_body),
        footer = {},
    ) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = stringResource(R.string.account_create_cta), onClick = onCreateAccountClick)
        }
    }
}

@PreviewLightDark
@Composable
private fun SignInPlaceholderScreenPreview() {
    RolaboxTheme {
        SignInPlaceholderScreen(onCreateAccountClick = {})
    }
}
