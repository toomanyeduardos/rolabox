package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.api.ShowInLibrary
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchEpisodeKey
import kotlin.time.Duration

/**
 * Connects a show opened from search to its ViewModel, which the entry creates (ADR-021; frame 32). The title is the
 * show's, from the key, while its feed is read. Follow is the first row, Off until it is turned On, which adds the show
 * to the library. Then every episode of the feed, in its order (ADR-043): one the library has with its dots and
 * download, and any other with the download arrow and no dot. A show listed oldest first opens on its next episode.
 * Center on one the library has is [onEpisodeClick], and on any other [onSearchEpisodeClick].
 */
@Composable
internal fun FoundShowRoute(
    viewModel: FoundShowViewModel,
    title: String,
    onEpisodeClick: (episodeId: Long) -> Unit,
    onSearchEpisodeClick: (PodcastSearchEpisodeKey) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val followFailed by viewModel.followFailed.collectAsStateWithLifecycle()
    val words = ShowWords(
        follow = stringResource(R.string.podcasts_follow),
        on = stringResource(R.string.podcasts_on),
        off = stringResource(R.string.podcasts_off),
        unknownEpisode = stringResource(R.string.podcasts_unknown_episode),
    )
    val rows = remember(state, words) { state.map { foundShowRows(it, words) } }
    ChangeFailedToast(followFailed, R.string.podcasts_follow_failed, onShow = viewModel::onFollowFailureShown)
    ScreenTitle(title)
    PodcastsWheelList(
        state = rows,
        onCenter = { index ->
            if (index == 0) {
                viewModel.onFollowClick()
            } else {
                when (val target = viewModel.episodeTarget(index - 1)) {
                    is FoundEpisodeTarget.Library -> onEpisodeClick(target.episodeId)
                    is FoundEpisodeTarget.Search -> onSearchEpisodeClick(episodeKeyOf(target.show, target.episode))
                    null -> Unit
                }
            }
        },
        startIndex = (state as? ListUiState.Loaded)?.value?.let { it.startingEpisode?.plus(1) ?: 0 },
    )
}

/** Follow with its value, then a row for each episode of the feed, in its order (frame 32; ADR-043). */
internal fun foundShowRows(content: FoundShowContent, words: ShowWords): List<DeviceListRow> {
    val follow = DeviceListRow(words.follow, value = if (content.isFollowed) words.on else words.off)
    val episodes = content.listed.map { index ->
        content.inLibrary?.episodes?.getOrNull(index)?.let { episodeRow(it, words.unknownEpisode) }
            ?: DeviceListRow(
                content.feed.episodes[index].title ?: words.unknownEpisode,
                download = DeviceListDownload.NotDownloaded,
            )
    }
    return listOf(follow) + episodes
}

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

private fun found(id: Long, title: String, creator: String) =
    DirectoryShow(DirectoryId.itunes(id), title, creator, "https://example.com/$id.xml")

private val FoundTheDaily = found(1, "The Daily", "NY Times")

private val FoundTheDailyFeed = ShowFeed(
    show = FoundTheDaily,
    ownerName = "NY Times",
    episodes = TheDailyEpisodes.map { episode ->
        DirectoryEpisode(
            guid = episode.audioUrl,
            title = episode.title,
            publishedAt = null,
            length = null,
            description = null,
            audioUrl = episode.audioUrl,
        )
    },
)

/** Frame 32: a show from search, Follow Off, every episode of its feed with the arrow and no dot. */
@PreviewLightDark
@Composable
private fun FoundShowPreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(
                    ListUiState.Loaded(
                        foundShowRows(FoundShowContent(FoundTheDailyFeed, inLibrary = null), showWords()),
                    ),
                    highlightedIndex = 0,
                )
            }
        }
    }
}

/** A show from search that the library has with Follow Off: the episodes it has show their dots and downloads. */
@PreviewLightDark
@Composable
private fun FoundShowInLibraryPreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val inLibrary = ShowInLibrary(
                    show = TheDaily.copy(isFollowed = false),
                    episodes = TheDailyEpisodes.map {
                        it.takeIf { episode ->
                            episode.download !=
                                DownloadState.NotDownloaded
                        }
                    },
                )
                PodcastsList(
                    ListUiState.Loaded(foundShowRows(FoundShowContent(FoundTheDailyFeed, inLibrary), showWords())),
                    highlightedIndex = 1,
                )
            }
        }
    }
}

/** A show from search whose feed couldn't be read. */
@PreviewLightDark
@Composable
private fun FoundShowUnreadablePreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.FeedUnreadable, highlightedIndex = 0)
            }
        }
    }
}

/** A show from search with no connection: frame 33's words. */
@PreviewLightDark
@Composable
private fun FoundShowOfflinePreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.NoConnection, highlightedIndex = 0)
            }
        }
    }
}

/** A show from search while its feed is read: a long one, on a slow connection, takes seconds. */
@PreviewLightDark
@Composable
private fun FoundShowLoadingPreview() {
    RolaboxTheme {
        DeviceScreen(title = "The Daily", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                PodcastsList(ListUiState.Loading, highlightedIndex = 0, loadingDelay = Duration.ZERO)
            }
        }
    }
}
