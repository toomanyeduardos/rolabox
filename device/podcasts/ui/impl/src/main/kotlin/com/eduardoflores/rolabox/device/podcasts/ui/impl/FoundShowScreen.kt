package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchEpisodeKey

/**
 * Connects a show opened from search to its ViewModel, which the entry creates (ADR-021; frame 32). The title is the
 * show's, from the key, while its feed is read. Follow is the first row, Off until it is turned On, which adds the show
 * to the library. Then every episode of the feed, newest first: one the library has with its dots and download, and
 * any other with the download arrow and no dot. Center on one the library has is [onEpisodeClick], and on any other
 * [onSearchEpisodeClick].
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
    FollowFailureToast(followFailed, onShow = viewModel::onFollowFailureShown)
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
    )
}

/** Follow with its value, then a row for each episode of the feed (frame 32). */
internal fun foundShowRows(content: FoundShowContent, words: ShowWords): List<DeviceListRow> {
    val follow = DeviceListRow(words.follow, value = if (content.isFollowed) words.on else words.off)
    val episodes = content.feed.episodes.mapIndexed { index, episode ->
        content.inLibrary?.episodes?.getOrNull(index)?.let { episodeRow(it, words.unknownEpisode) }
            ?: DeviceListRow(episode.title ?: words.unknownEpisode, download = DeviceListDownload.NotDownloaded)
    }
    return listOf(follow) + episodes
}
