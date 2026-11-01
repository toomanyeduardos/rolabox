package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceThinProgressBar
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.rememberListHighlight
import com.eduardoflores.rolabox.device.library.api.Progress

/** The rows of the Music menu, in the order they are shown. */
private enum class MusicMenuRow { Artists, Albums, Songs, Genres, Playlists }

/**
 * Music: its menu, or why there is none. Turning moves the highlight, which stops at the first and last
 * rows. Center on Artists reports [onArtistsClick]; the other rows do nothing until their screens exist.
 * The first time it opens, it asks for the audio permission (ADR-024). Center on the row that takes the
 * menu's place asks again, or opens system settings when Android won't.
 */
@Composable
internal fun MusicMenuRoute(viewModel: MusicMenuViewModel, onArtistsClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlight = rememberListHighlight()
    val permission = rememberAudioPermissionStep(viewModel::onPermissionResult)
    val askForPermission = {
        viewModel.onPermissionRequested()
        permission.request()
    }
    ScreenTitle(stringResource(R.string.music_title))
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed(permission.canAskAgain()) }
    LaunchedEffect(state) {
        if (state == MusicMenuUiState.PermissionNotAsked) askForPermission()
    }
    HandleWheelEvents { event ->
        when (state) {
            is MusicMenuUiState.Menu -> when (event) {
                is WheelEvent.Turn -> highlight.move(event.steps, MusicMenuRow.entries.size)

                WheelEvent.Center -> when (MusicMenuRow.entries[highlight.index]) {
                    MusicMenuRow.Artists -> onArtistsClick()
                    MusicMenuRow.Albums, MusicMenuRow.Songs, MusicMenuRow.Genres, MusicMenuRow.Playlists -> Unit
                }

                else -> Unit
            }

            MusicMenuUiState.PermissionNotAsked, MusicMenuUiState.PermissionDenied ->
                if (event == WheelEvent.Center) askForPermission()

            MusicMenuUiState.PermissionPermanentlyDenied ->
                if (event == WheelEvent.Center) permission.openSettings()

            MusicMenuUiState.Loading, MusicMenuUiState.Scanning, MusicMenuUiState.Empty, MusicMenuUiState.Failed -> Unit
        }
    }
    MusicMenuScreen(state, highlightedIndex = highlight.index)
}

/** The stateless screen that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun MusicMenuScreen(state: MusicMenuUiState, highlightedIndex: Int) {
    when (state) {
        // A frame, until the library's first permission check ends.
        MusicMenuUiState.Loading -> Unit

        MusicMenuUiState.PermissionNotAsked, MusicMenuUiState.PermissionDenied ->
            PermissionRow(stringResource(R.string.music_permission_denied))

        MusicMenuUiState.PermissionPermanentlyDenied ->
            PermissionRow(stringResource(R.string.music_permission_permanently_denied))

        MusicMenuUiState.Scanning -> DeviceMessage(
            stringResource(R.string.music_scanning_title),
            stringResource(R.string.music_scanning_body),
        )

        MusicMenuUiState.Empty -> DeviceMessage(
            stringResource(R.string.music_empty_title),
            stringResource(R.string.music_empty_body),
        )

        is MusicMenuUiState.Menu -> MusicMenu(highlightedIndex, state.readingTags)

        MusicMenuUiState.Failed -> DeviceMessage(stringResource(R.string.music_library_unavailable))
    }
}

// No ›: center brings up Android's prompt or leaves for system settings, and opens no device screen.
@Composable
private fun PermissionRow(text: String) {
    DeviceList(rows = listOf(DeviceListRow(text)), highlightedIndex = 0)
}

@Composable
private fun MusicMenu(highlightedIndex: Int, readingTags: Progress?) {
    val rows = MusicMenuRow.entries.map { row ->
        val label = when (row) {
            MusicMenuRow.Artists -> stringResource(R.string.music_menu_artists)
            MusicMenuRow.Albums -> stringResource(R.string.music_menu_albums)
            MusicMenuRow.Songs -> stringResource(R.string.music_menu_songs)
            MusicMenuRow.Genres -> stringResource(R.string.music_menu_genres)
            MusicMenuRow.Playlists -> stringResource(R.string.music_menu_playlists)
        }
        DeviceListRow(label, opensSubmenu = true)
    }
    Column(Modifier.fillMaxSize()) {
        DeviceList(rows = rows, highlightedIndex = highlightedIndex, modifier = Modifier.weight(1f))
        if (readingTags != null && readingTags.total > 0) {
            DeviceThinProgressBar(
                progress = readingTags.done.toFloat() / readingTags.total,
                description = stringResource(R.string.music_reading_tags),
            )
        }
    }
}
