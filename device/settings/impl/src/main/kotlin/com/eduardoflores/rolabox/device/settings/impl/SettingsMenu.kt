package com.eduardoflores.rolabox.device.settings.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.rememberListHighlight

/**
 * The settings list, a device screen (ADR-018, rule 11). Turning moves the highlight, which stops at
 * the first and last rows. Center on Clicker or Theme changes its value in place, without leaving the
 * list. Center on Main Menu reports [onMainMenuClick], on About [onAboutClick], and on a contributed
 * section's row [onOpenFullScreen] with the key it opens, the generic exit that leaves the display
 * (ADR-020, rule 17). The highlight is saved with the entry (ADR-018, rule 7).
 */
@Composable
internal fun SettingsRoute(
    viewModel: SettingsViewModel,
    onMainMenuClick: () -> Unit,
    onAboutClick: () -> Unit,
    onOpenFullScreen: (NavKey) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rows = settingsListRows(state.sections)
    val highlight = rememberListHighlight()
    ScreenTitle(stringResource(R.string.settings_title))
    SaveFailureToast(saveFailed = state.saveFailed, onShow = viewModel::onSaveFailureShown)
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, rows.size)

            WheelEvent.Center -> when (val row = rows.getOrNull(highlight.index)) {
                SettingsListRow.Clicker -> viewModel.onClickerChange()
                SettingsListRow.Theme -> viewModel.onThemeChange()
                SettingsListRow.MainMenu -> onMainMenuClick()
                SettingsListRow.About -> onAboutClick()
                is SettingsListRow.Section -> row.row.opens?.let(onOpenFullScreen)
                null -> Unit
            }

            else -> Unit
        }
    }
    SettingsList(state = state, highlightedIndex = highlight.index)
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun SettingsList(state: SettingsUiState, highlightedIndex: Int) {
    val rows = settingsListRows(state.sections).map { row ->
        when (row) {
            SettingsListRow.Clicker ->
                DeviceListRow(
                    stringResource(R.string.settings_clicker),
                    value = state.clicker?.let {
                        stringResource(it.label)
                    },
                )

            SettingsListRow.Theme ->
                DeviceListRow(
                    stringResource(R.string.settings_theme),
                    value = state.darkTheme?.let {
                        stringResource(it.label)
                    },
                )

            SettingsListRow.MainMenu -> DeviceListRow(stringResource(R.string.settings_main_menu), opensSubmenu = true)

            SettingsListRow.About -> DeviceListRow(stringResource(R.string.settings_about), opensSubmenu = true)

            is SettingsListRow.Section -> DeviceListRow(row.row.title(), opensSubmenu = row.row.opens != null)
        }
    }
    DeviceList(rows = rows, highlightedIndex = highlightedIndex)
}

@get:StringRes
private val Clicker.label: Int
    get() = when (this) {
        Clicker.ON -> R.string.settings_on
        Clicker.OFF -> R.string.settings_off
    }

@get:StringRes
private val DarkThemeConfig.label: Int
    get() = when (this) {
        DarkThemeConfig.FOLLOW_SYSTEM -> R.string.settings_theme_follow_system
        DarkThemeConfig.LIGHT -> R.string.settings_theme_light
        DarkThemeConfig.DARK -> R.string.settings_theme_dark
    }
