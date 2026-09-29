package com.eduardoflores.rolabox.core.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eduardoflores.rolabox.core.auth.ui.google.GoogleButton

/** A provider's own button, in the branding its guidelines require. The look isn't themed. */
@Composable
fun SignInButton(
    provider: SignInProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (provider) {
        SignInProvider.Google -> GoogleButton(onClick = onClick, modifier = modifier, enabled = enabled)
    }
}
