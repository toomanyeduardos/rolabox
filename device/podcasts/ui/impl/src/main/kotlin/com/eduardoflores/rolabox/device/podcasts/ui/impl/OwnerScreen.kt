package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects an owner's shows to its ViewModel, which the entry creates (ADR-021). The title is the owner's name, and
 * center on a show reports [onShowClick] with its id (frame 24).
 */
@Composable
internal fun OwnerRoute(viewModel: OwnerViewModel, onShowClick: (showId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val rows = remember(state) { state.map { shows -> shows.map { DeviceListRow(it.title, opensSubmenu = true) } } }
    ScreenTitle(title?.let { it.name ?: stringResource(R.string.podcasts_unknown_owner) })
    PodcastsWheelList(
        state = rows,
        onCenter = { index ->
            (state as? ListUiState.Loaded)?.value?.getOrNull(index)?.let { onShowClick(it.id.value) }
        },
        sortedFrom = 0,
    )
}

/** Frame 24: an owner's shows, A to Z, the highlight on The Daily. */
@PreviewLightDark
@Composable
private fun OwnerPreview() {
    RolaboxTheme {
        DeviceScreen(title = "NY Times", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val rows = listOf("Hard Fork", "The Daily", "The Ezra Klein Show").map {
                    DeviceListRow(it, opensSubmenu = true)
                }
                PodcastsList(ListUiState.Loaded(rows), highlightedIndex = 1)
            }
        }
    }
}
