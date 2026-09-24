package com.eduardoflores.rolabox

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// The offline flavor has no account features (ADR-008). Empty until the library screen exists.
@Composable
internal fun HomeContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}
