package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.rememberListHighlight

/**
 * Main Menu, a device screen: every row of the main menu that can be hidden, with On or Off. Turning
 * moves the highlight, which stops at the first and last rows, and center changes the highlighted
 * row in place. Settings and Now Playing can't be hidden, so they aren't listed (ADR-020, rule 15).
 */
@Composable
internal fun MainMenuSettingsRoute(viewModel: MainMenuSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlight = rememberListHighlight()
    ScreenTitle(stringResource(R.string.settings_main_menu))
    SaveFailureToast(saveFailed = state.saveFailed, onShow = viewModel::onSaveFailureShown)
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, MainMenuRow.entries.size)
            WheelEvent.Center -> viewModel.onRowChange(MainMenuRow.entries[highlight.index])
            else -> Unit
        }
    }
    MainMenuSettingsList(rows = state.rows, highlightedIndex = highlight.index)
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun MainMenuSettingsList(rows: Map<MainMenuRow, MainMenuRowVisibility>?, highlightedIndex: Int) {
    val listRows = MainMenuRow.entries.map { row ->
        val label = when (row) {
            MainMenuRow.MUSIC -> stringResource(R.string.settings_main_menu_music)
            MainMenuRow.PODCASTS -> stringResource(R.string.settings_main_menu_podcasts)
            MainMenuRow.AUDIOBOOKS -> stringResource(R.string.settings_main_menu_audiobooks)
            MainMenuRow.SHUFFLE_SONGS -> stringResource(R.string.settings_main_menu_shuffle_songs)
        }
        val value = when (rows?.get(row)) {
            MainMenuRowVisibility.SHOWN -> stringResource(R.string.settings_on)
            MainMenuRowVisibility.HIDDEN -> stringResource(R.string.settings_off)
            null -> null
        }
        DeviceListRow(label, value = value)
    }
    DeviceList(rows = listRows, highlightedIndex = highlightedIndex)
}
