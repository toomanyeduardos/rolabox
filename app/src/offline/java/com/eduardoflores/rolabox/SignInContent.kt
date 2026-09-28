package com.eduardoflores.rolabox

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// The offline flavor has no account features (ADR-008), and it always starts on Home, so this is never
// shown.
@Composable
internal fun SignInContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}
