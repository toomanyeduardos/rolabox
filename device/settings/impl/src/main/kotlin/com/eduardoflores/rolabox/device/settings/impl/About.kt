package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * About, a device screen: the app's name and version, and the number of songs in the library. There
 * is nothing to open or change, so nothing is highlighted and the screen takes no wheel events. MENU,
 * the host's, goes back.
 */
@Composable
internal fun AboutRoute(viewModel: AboutViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenTitle(stringResource(R.string.settings_about))
    AboutList(state)
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun AboutList(state: AboutUiState) {
    val songs = when (val songs = state.songs) {
        SongCount.Loading -> null
        is SongCount.Loaded -> songs.count.toString()
        SongCount.Unavailable -> stringResource(R.string.settings_about_unavailable)
    }
    val rows = listOf(
        DeviceListRow(stringResource(R.string.settings_about_app_name)),
        DeviceListRow(stringResource(R.string.settings_about_version), value = state.version),
        DeviceListRow(stringResource(R.string.settings_about_songs), value = songs),
    )
    DeviceList(rows = rows, highlightedIndex = NO_HIGHLIGHT)
}

private const val NO_HIGHLIGHT = -1

// The number of songs frame 13 shows.
private const val PREVIEW_SONG_COUNT = 42

@PreviewLightDark
@Composable
private fun AboutPreview() {
    RolaboxTheme {
        DeviceScreen(title = "About", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                AboutList(AboutUiState(version = "1.0", songs = SongCount.Loaded(PREVIEW_SONG_COUNT)))
            }
        }
    }
}
