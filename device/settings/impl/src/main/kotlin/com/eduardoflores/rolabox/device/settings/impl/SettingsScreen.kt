package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.SecondaryButton
import com.eduardoflores.rolabox.common.designsystem.component.WordmarkHeader
import com.eduardoflores.rolabox.common.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType
import com.eduardoflores.rolabox.device.settings.api.SettingsSection

/**
 * Connects [SettingsScreen] to the contributed [sections], already in the order of their slots. A
 * row opens its section's screen through [onOpenFullScreen], the generic exit (ADR-020, rule 17).
 */
@Composable
internal fun SettingsRoute(
    sections: List<SettingsSection>,
    onOpenFullScreen: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        rows = sections.map { it.row.title() },
        onRowClick = { index -> onOpenFullScreen(sections[index].row.opens) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** The settings list: one row per section. What Settings holds of its own comes with 37.11. */
@Composable
internal fun SettingsScreen(
    rows: List<String>,
    onRowClick: (index: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .brushedMetal()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        WordmarkHeader(onBack = onBack)
        Text(text = stringResource(R.string.settings_title), style = RolaboxType.styles.screenTitle)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { index, title ->
                SecondaryButton(text = title, onClick = { onRowClick(index) })
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    RolaboxTheme {
        SettingsScreen(rows = listOf("Account"), onRowClick = {}, onBack = {})
    }
}
