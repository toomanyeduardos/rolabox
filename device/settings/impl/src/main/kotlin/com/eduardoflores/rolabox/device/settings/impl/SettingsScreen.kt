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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.ChoiceGroup
import com.eduardoflores.rolabox.common.designsystem.component.ChoiceKey
import com.eduardoflores.rolabox.common.designsystem.component.FieldError
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.RowKey
import com.eduardoflores.rolabox.common.designsystem.component.WordmarkHeader
import com.eduardoflores.rolabox.common.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.device.settings.api.SettingsRow

/**
 * Connects [SettingsScreen] to [viewModel]. A row of a contributed section opens its screen through
 * [onOpenFullScreen], the generic exit (ADR-020, rule 17).
 */
@Composable
internal fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenFullScreen: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onRowClick = onOpenFullScreen,
        onDarkThemeSelect = viewModel::onDarkThemeSelect,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Settings, an ordinary screen operated by touch (ADR-018, rule 11): the contributed sections come
 * first, in the order of their slots, then what Settings holds of its own.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onRowClick: (NavKey) -> Unit,
    onDarkThemeSelect: (DarkThemeConfig) -> Unit,
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
            state.sections.forEach { row ->
                val opens = row.opens
                RowKey(
                    title = row.title(),
                    detail = row.detail?.invoke(),
                    onClick = opens?.let { { onRowClick(it) } },
                )
            }
        }
        ThemeChoice(selected = state.darkTheme, saveFailed = state.themeSaveFailed, onSelect = onDarkThemeSelect)
    }
}

@Composable
private fun ThemeChoice(selected: DarkThemeConfig?, saveFailed: Boolean, onSelect: (DarkThemeConfig) -> Unit) {
    ChoiceGroup(label = stringResource(R.string.settings_theme)) {
        DarkThemeConfig.entries.forEach { config ->
            ChoiceKey(
                text = stringResource(config.label()),
                selected = config == selected,
                onClick = { onSelect(config) },
            )
        }
        if (saveFailed) FieldError(message = stringResource(R.string.settings_theme_save_failed))
    }
}

private fun DarkThemeConfig.label(): Int = when (this) {
    DarkThemeConfig.FOLLOW_SYSTEM -> R.string.settings_theme_follow_system
    DarkThemeConfig.LIGHT -> R.string.settings_theme_light
    DarkThemeConfig.DARK -> R.string.settings_theme_dark
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    RolaboxTheme {
        SettingsScreen(
            state = SettingsUiState(
                sections = listOf(SettingsRow(title = { "Account" }, detail = { "Sign in" }, opens = PreviewKey)),
                darkTheme = DarkThemeConfig.FOLLOW_SYSTEM,
            ),
            onRowClick = {},
            onDarkThemeSelect = {},
            onBack = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenSignedInPreview() {
    RolaboxTheme {
        SettingsScreen(
            state = SettingsUiState(
                sections = listOf(SettingsRow(title = { "Account" }, detail = { "Signed in as Eduardo Flores" })),
                darkTheme = DarkThemeConfig.DARK,
                themeSaveFailed = true,
            ),
            onRowClick = {},
            onDarkThemeSelect = {},
            onBack = {},
        )
    }
}

private data object PreviewKey : NavKey
