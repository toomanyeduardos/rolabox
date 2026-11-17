package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.podcasts.api.Owner

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
