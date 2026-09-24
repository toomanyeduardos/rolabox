package com.eduardoflores.rolabox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eduardoflores.rolabox.feature.account.AuthenticationScreen

// :feature:account is a cloud-only dependency (ADR-008), so only this flavor can show account screens.
@Composable
internal fun HomeContent(modifier: Modifier = Modifier) {
    AuthenticationScreen(modifier = modifier)
}
