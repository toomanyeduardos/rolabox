@file:Suppress("TooManyFunctions") // One for each state of the screens (ADR-016).

package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import kotlin.time.Duration

// The screens as the designs show them (player-designs/device, frames 09 and 22 to 25), on the assembled device. They
// call the stateless list with the rows the routes build (ADR-016, rule 3).

private val Owners = listOf("Field Notes Media", "Harbor Audio", "NY Times", "Small Rooms", "Weekly Bench")
    .map { Owner(OwnerId(it.lowercase()), it) }

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
private fun DevicePreview(title: String, record: DeviceRecord = DeviceRecord.Hidden, content: @Composable () -> Unit) {
    RolaboxTheme {
        DeviceScreen(title = title, batteryLevel = 0.7f, onWheelEvent = {}, record = record) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun ownerRows(owners: List<Owner>): List<DeviceListRow> = podcastsRows(
    owners,
    search = stringResource(R.string.podcasts_search),
    unknownOwner = stringResource(R.string.podcasts_unknown_owner),
)

@Composable
private fun showWords() = ShowWords(
    follow = stringResource(R.string.podcasts_follow),
    on = stringResource(R.string.podcasts_on),
    off = stringResource(R.string.podcasts_off),
    unknownEpisode = stringResource(R.string.podcasts_unknown_episode),
)

/** Frame 22: Search, then the owners A to Z, the highlight on NY Times. */
@PreviewLightDark
@Composable
private fun PodcastsPreview() = DevicePreview(title = "Podcasts") {
    PodcastsList(ListUiState.Loaded(ownerRows(Owners)), highlightedIndex = 3)
}

/** Frame 09: the same list while music plays, with the spinning record in the header. */
@PreviewLightDark
@Composable
private fun PodcastsWhilePlayingPreview() = DevicePreview(title = "Podcasts", record = DeviceRecord.Spinning) {
    PodcastsList(ListUiState.Loaded(ownerRows(Owners)), highlightedIndex = 3)
}

/** Frame 23: no podcasts yet, so Search is alone and highlighted, with the message under it. */
@PreviewLightDark
@Composable
private fun NoPodcastsPreview() = DevicePreview(title = "Podcasts") {
    PodcastsList(
        ListUiState.Loaded(ownerRows(emptyList())),
        highlightedIndex = 0,
        message = DeviceListMessage(
            stringResource(R.string.podcasts_none_title),
            stringResource(R.string.podcasts_none_body),
        ),
    )
}

/** The owner with no name, last. */
@PreviewLightDark
@Composable
private fun UnknownOwnerPreview() = DevicePreview(title = "Podcasts") {
    val owners = Owners + Owner(OwnerId(""), null)
    PodcastsList(ListUiState.Loaded(ownerRows(owners)), highlightedIndex = owners.size)
}

/** Frame 24: an owner's shows, A to Z, the highlight on The Daily. */
@PreviewLightDark
@Composable
private fun OwnerPreview() = DevicePreview(title = "NY Times") {
    val rows = listOf("Hard Fork", "The Daily", "The Ezra Klein Show").map { DeviceListRow(it, opensSubmenu = true) }
    PodcastsList(ListUiState.Loaded(rows), highlightedIndex = 1)
}

/** Frame 25: Follow On, then the episodes newest first, the highlight on the half dot. */
@PreviewLightDark
@Composable
private fun ShowPreview() = DevicePreview(title = "The Daily") {
    PodcastsList(
        ListUiState.Loaded(showRows(ShowContent(TheDaily, TheDailyEpisodes), showWords())),
        highlightedIndex = 4,
    )
}

/** Follow Off: only the episodes with a download, done or in progress, and the highlight on Follow. */
@PreviewLightDark
@Composable
private fun ShowFollowOffPreview() = DevicePreview(title = "The Daily") {
    val downloaded = TheDailyEpisodes.filter { it.download != DownloadState.NotDownloaded }
    val content = ShowContent(TheDaily.copy(isFollowed = false), downloaded)
    PodcastsList(ListUiState.Loaded(showRows(content, showWords())), highlightedIndex = 0)
}

/** The show was removed while its screen was open: MENU goes back. */
@PreviewLightDark
@Composable
private fun GonePreview() = DevicePreview(title = "The Daily") {
    PodcastsList(ListUiState.Gone, highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun FailedPreview() = DevicePreview(title = "Podcasts") {
    PodcastsList(ListUiState.Failed, highlightedIndex = 0)
}
