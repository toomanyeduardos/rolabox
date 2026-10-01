package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

/**
 * The app's top bar. Material's bar has a fixed height, so this one scales it with the font scale:
 * the title is never cut short, and wraps to a second line when it doesn't fit on one (ADR-017).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolaboxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(text = title, textAlign = TextAlign.Center)
        },
        modifier = modifier,
        expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight * LocalDensity.current.fontScale.coerceAtLeast(1f),
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@PreviewLightDark
@Composable
private fun RolaboxTopBarPreview() {
    RolaboxTheme {
        RolaboxTopBar(
            title = "Rolabox",
            actions = {
                TextButton(onClick = {}) { Text("Edit") }
            },
        )
    }
}
