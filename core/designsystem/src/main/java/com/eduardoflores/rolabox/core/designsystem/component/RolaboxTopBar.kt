package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

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
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
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
