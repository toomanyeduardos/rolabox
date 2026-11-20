package com.eduardoflores.rolabox.device.podcasts.ui.impl

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDot
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import kotlin.time.Duration

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
internal fun FollowFailureToast(failed: Boolean, onShow: () -> Unit) {
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

private val TheDaily = Show(ShowId(1), DirectoryId.itunes(1), "The Daily", OwnerId("ny times"), "NY Times", true)

private val TheDailyEpisodes = listOf(
    episode(1, "A Heat Wave Over the North Atlantic", PlayedState.Unplayed, DownloadState.NotDownloaded),
    episode(2, "Inside the Race for a Cheaper Battery", PlayedState.Unplayed, DownloadState.Downloading(0.38f)),
    episode(3, "The Town That Saved Its Pharmacy", PlayedState.Unplayed, DownloadState.Downloaded),
    episode(
        4,
        "What Happened at the Port Strike, and What Comes Next",
        PlayedState.PartlyPlayed,
        DownloadState.Downloaded,
    ),
    episode(5, "A Quiet Election in a Loud Year", PlayedState.Played, DownloadState.Downloaded),
    episode(6, "The Return of the Night Train", PlayedState.Played, DownloadState.Downloaded),
    episode(7, "Who Owns the Moon?", PlayedState.Played, DownloadState.Downloaded),
)

private fun episode(id: Long, title: String, played: PlayedState, download: DownloadState) = Episode(
    id = EpisodeId(id),
    showId = TheDaily.id,
    title = title,
    publishedAt = null,
    length = null,
    description = null,
    audioUrl = "https://example.com/$id.mp3",
    audioSizeBytes = null,
    playedState = played,
    position = Duration.ZERO,
    download = download,
)

@Composable
private fun showWords() = ShowWords(
    follow = stringResource(R.string.podcasts_follow),
    on = stringResource(R.string.podcasts_on),
    off = stringResource(R.string.podcasts_off),
    unknownEpisode = stringResource(R.string.podcasts_unknown_episode),
)

/** Frame 25: Follow On, then the episodes newest first, the highlight on the half dot. */
@PreviewLightDark
@Composable
private fun ShowPreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(
                    ListUiState.Loaded(showRows(ShowContent(TheDaily, TheDailyEpisodes), showWords())),
                    highlightedIndex = 4,
                )
            }
        }
    }
}

/** Follow Off: only the episodes with a download, done or in progress, and the highlight on Follow. */
@PreviewLightDark
@Composable
private fun ShowFollowOffPreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val downloaded = TheDailyEpisodes.filter { it.download != DownloadState.NotDownloaded }
                val content = ShowContent(TheDaily.copy(isFollowed = false), downloaded)
                PodcastsList(ListUiState.Loaded(showRows(content, showWords())), highlightedIndex = 0)
            }
        }
    }
}

/** The show was removed while its screen was open: MENU goes back. */
@PreviewLightDark
@Composable
private fun GonePreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.Gone, highlightedIndex = 0)
            }
        }
    }
}
