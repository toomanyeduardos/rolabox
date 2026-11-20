package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId

/**
 * Connects Podcasts to its ViewModel (ADR-021). Search is always the first row, then the owners A to Z (frame 22).
 * With no owner, Search is alone, with a message under it (frame 23). Center on Search reports [onSearchClick], and on
 * an owner [onOwnerClick] with its grouping key.
 */
@Composable
internal fun PodcastsRoute(
    viewModel: PodcastsViewModel,
    onSearchClick: () -> Unit,
    onOwnerClick: (ownerId: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val search = stringResource(R.string.podcasts_search)
    val unknownOwner = stringResource(R.string.podcasts_unknown_owner)
    val rows = remember(state, search, unknownOwner) { state.map { podcastsRows(it, search, unknownOwner) } }
    val noneYet = DeviceListMessage(
        stringResource(R.string.podcasts_none_title),
        stringResource(R.string.podcasts_none_body),
    )
    ScreenTitle(stringResource(R.string.podcasts_title))
    PodcastsWheelList(
        state = rows,
        onCenter = { index ->
            if (index == SEARCH_INDEX) {
                onSearchClick()
            } else {
                (state as? ListUiState.Loaded)?.value?.getOrNull(index - 1)?.let { onOwnerClick(it.id.value) }
            }
        },
        sortedFrom = SEARCH_INDEX + 1,
        message = noneYet.takeIf { (state as? ListUiState.Loaded)?.value?.isEmpty() == true },
    )
}

/** Search, then a row for each owner, which opens its shows. */
internal fun podcastsRows(owners: List<Owner>, search: String, unknownOwner: String): List<DeviceListRow> =
    listOf(DeviceListRow(search, opensSubmenu = true)) +
        owners.map { DeviceListRow(it.name ?: unknownOwner, opensSubmenu = true) }

private const val SEARCH_INDEX = 0

private val Owners = listOf("Field Notes Media", "Harbor Audio", "NY Times", "Small Rooms", "Weekly Bench")
    .map { Owner(OwnerId(it.lowercase()), it) }

@Composable
private fun ownerRows(owners: List<Owner>): List<DeviceListRow> = podcastsRows(
    owners,
    search = stringResource(R.string.podcasts_search),
    unknownOwner = stringResource(R.string.podcasts_unknown_owner),
)

/** Frame 22: Search, then the owners A to Z, the highlight on NY Times. */
@PreviewLightDark
@Composable
private fun PodcastsPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Podcasts", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.Loaded(ownerRows(Owners)), highlightedIndex = 3)
            }
        }
    }
}

/** Frame 09: the same list while music plays, with the spinning record in the header. */
@PreviewLightDark
@Composable
private fun PodcastsWhilePlayingPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Podcasts", batteryLevel = 0.7f, onWheelEvent = {}, record = DeviceRecord.Spinning) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.Loaded(ownerRows(Owners)), highlightedIndex = 3)
            }
        }
    }
}

/** Frame 23: no podcasts yet, so Search is alone and highlighted, with the message under it. */
@PreviewLightDark
@Composable
private fun NoPodcastsPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Podcasts", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(
                    ListUiState.Loaded(ownerRows(emptyList())),
                    highlightedIndex = 0,
                    message = DeviceListMessage(
                        stringResource(R.string.podcasts_none_title),
                        stringResource(R.string.podcasts_none_body),
                    ),
                )
            }
        }
    }
}

/** The owner with no name, last. */
@PreviewLightDark
@Composable
private fun UnknownOwnerPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Podcasts", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val owners = Owners + Owner(OwnerId(""), null)
                PodcastsList(ListUiState.Loaded(ownerRows(owners)), highlightedIndex = owners.size)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FailedPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Podcasts", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.Failed, highlightedIndex = 0)
            }
        }
    }
}
