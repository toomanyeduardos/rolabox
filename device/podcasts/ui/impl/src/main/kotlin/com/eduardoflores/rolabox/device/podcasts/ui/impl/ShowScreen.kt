package com.eduardoflores.rolabox.device.podcasts.ui.impl

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDot
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState

/**
 * Connects a show to its ViewModel, which the entry creates (ADR-021). The title is the show's. Follow is the first
 * row, with On or Off, and center turns it in place. Then the episodes, newest first, with their dots and downloads
 * (frame 25). Center on an episode reports [onEpisodeClick] with its id.
 */
@Composable
internal fun ShowRoute(viewModel: ShowViewModel, onEpisodeClick: (episodeId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val followFailed by viewModel.followFailed.collectAsStateWithLifecycle()
    val words = ShowWords(
        follow = stringResource(R.string.podcasts_follow),
        on = stringResource(R.string.podcasts_on),
        off = stringResource(R.string.podcasts_off),
        unknownEpisode = stringResource(R.string.podcasts_unknown_episode),
    )
    val rows = remember(state, words) { state.map { showRows(it, words) } }
    FollowFailureToast(followFailed, onShow = viewModel::onFollowFailureShown)
    ScreenTitle((state as? ListUiState.Loaded)?.value?.show?.title)
    PodcastsWheelList(
        state = rows,
        onCenter = { index ->
            if (index == FOLLOW_INDEX) {
                viewModel.onFollowClick()
            } else {
                (state as? ListUiState.Loaded)?.value?.episodes?.getOrNull(index - 1)?.let {
                    onEpisodeClick(it.id.value)
                }
            }
        },
    )
}

/** The screen's words, read once from the resources so that the rows can be built outside composition. */
internal data class ShowWords(val follow: String, val on: String, val off: String, val unknownEpisode: String)

/** Follow with its value, then a row for each episode. */
internal fun showRows(content: ShowContent, words: ShowWords): List<DeviceListRow> =
    listOf(DeviceListRow(words.follow, value = if (content.show.isFollowed) words.on else words.off)) +
        content.episodes.map { episodeRow(it, words.unknownEpisode) }

/**
 * An episode's row: a full dot for unplayed, a half dot for partly played and none for played, and after the title
 * the arrow when it isn't downloaded, or its percentage while it downloads (ADR-031).
 */
internal fun episodeRow(episode: Episode, unknownTitle: String): DeviceListRow = DeviceListRow(
    label = episode.title ?: unknownTitle,
    dot = when (episode.playedState) {
        PlayedState.Unplayed -> DeviceListDot.Unplayed
        PlayedState.PartlyPlayed -> DeviceListDot.PartlyPlayed
        PlayedState.Played -> null
    },
    download = when (val download = episode.download) {
        DownloadState.NotDownloaded -> DeviceListDownload.NotDownloaded
        is DownloadState.Downloading -> DeviceListDownload.Downloading(percentOf(download.fraction))
        DownloadState.Downloaded -> null
    },
)

/** The whole percent done, never rounded up, so 100% is only shown once the download is. */
private fun percentOf(fraction: Float): Int =
    (fraction.toDouble() * PERCENT + ROUNDING_SLACK).toInt().coerceIn(0, PERCENT)

/**
 * Follow that couldn't be changed says so, once, in a toast, and then calls [onShow]: the row keeps its value, and a
 * failed action is never silent (ADR-007). A toast is the system's, drawn over the device (ADR-018).
 */
@Composable
private fun FollowFailureToast(failed: Boolean, onShow: () -> Unit) {
    val context = LocalContext.current
    val currentOnShow by rememberUpdatedState(onShow)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        Toast.makeText(context, R.string.podcasts_follow_failed, Toast.LENGTH_SHORT).show()
        currentOnShow()
    }
}

private const val FOLLOW_INDEX = 0
private const val PERCENT = 100

// A fraction such as 0.38f is a hair under it as a float, and is still 38%.
private const val ROUNDING_SLACK = 1e-4
