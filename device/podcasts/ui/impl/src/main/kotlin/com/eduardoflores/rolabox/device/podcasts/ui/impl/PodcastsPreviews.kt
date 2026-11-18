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
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// The screens as the designs show them (player-designs/device, frames 09, 22 to 26 and 32), on the assembled
// device. They call the stateless list with the rows the routes build (ADR-016, rule 3).

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

// The Episode screen. The previews' today is in the year of the designs, so the dates show no year.
private val PreviewToday = LocalDate.parse("2026-11-17")

private const val PORT_STRIKE =
    "Dockworkers on both coasts walked off the job for nine days. We look at what the strike won, " +
        "what it cost the ports, and why the next contract talks may be harder. Then the deal's fine " +
        "print: what it means for shipping prices this winter, for the warehouses inland that wait on " +
        "the ports, and for the workers who will be back at the table in three years, with automation " +
        "the one question no one could settle."

private const val THE_TALKS =
    "Our reporters spent the strike on the docks in Los Angeles, Savannah and Newark. They talked to crane " +
        "operators, truck drivers waiting in lines that stretched for miles, and the shippers who rerouted " +
        "their cargo to Canada and Mexico. Some of that cargo may never come back. We also hear from the " +
        "negotiators on both sides, who describe the night the deal came together, and what each of them " +
        "had to give up to get there."

private const val WHATS_NEXT =
    "Finally, what comes next. The contract runs for six years, but the question of automation was only " +
        "set aside, not settled, and both sides say they are already preparing for the next round. We look " +
        "at the ports in Rotterdam and Singapore, where machines already move most of the containers, and " +
        "ask what American ports would look like if they followed. Plus: a listener asks why her furniture " +
        "order took four months, and we trace its trip from the factory to her front door, one port at a time."

private fun partOfTheDaily(position: Duration, played: PlayedState, description: String? = PORT_STRIKE) =
    TheDailyEpisodes.first { it.playedState == PlayedState.PartlyPlayed }.copy(
        publishedAt = Instant.parse("2026-11-08T12:00:00Z"),
        length = 32.minutes,
        position = position,
        playedState = played,
        description = description,
    )

@Composable
private fun EpisodePreview(content: EpisodeContent, wheel: EpisodeWheel = EpisodeWheel()) =
    DevicePreview(title = content.showTitle) {
        EpisodeScreen(
            state = ListUiState.Loaded(content),
            wheel = wheel,
            today = PreviewToday,
            zone = ZoneOffset.UTC,
            onScrollableLinesChange = {},
        )
    }

/** Frame 26: partly played, so Resume and Play from Start, with the time left, and the highlight on Resume. */
@PreviewLightDark
@Composable
private fun EpisodePartlyPlayedPreview() = EpisodePreview(
    episodeContent(TheDaily, partOfTheDaily(12.minutes, PlayedState.PartlyPlayed)),
)

/** An unplayed episode: Play only, and no time left. */
@PreviewLightDark
@Composable
private fun EpisodeUnplayedPreview() =
    EpisodePreview(episodeContent(TheDaily, partOfTheDaily(Duration.ZERO, PlayedState.Unplayed)))

/** Frame 36: not downloaded, on mobile data with Use Mobile Data off, so Needs Wi-Fi in place of Play. */
@PreviewLightDark
@Composable
private fun EpisodeNeedsWifiPreview() = EpisodePreview(
    episodeContent(
        TheDaily,
        partOfTheDaily(Duration.ZERO, PlayedState.Unplayed).copy(download = DownloadState.NotDownloaded),
        needWifi = true,
    ),
)

/** Turned past Play from Start: the description scrolled up, Play from Start still highlighted. */
@PreviewLightDark
@Composable
private fun EpisodeScrolledPreview() = EpisodePreview(
    episodeContent(
        TheDaily,
        partOfTheDaily(12.minutes, PlayedState.PartlyPlayed, "$PORT_STRIKE\n\n$THE_TALKS\n\n$WHATS_NEXT"),
    ),
    wheel = EpisodeWheel(row = 1, lines = 4),
)

/** An episode whose feed has no description: the info line alone under the rows. */
@PreviewLightDark
@Composable
private fun EpisodeNoDescriptionPreview() = EpisodePreview(
    episodeContent(TheDaily, partOfTheDaily(Duration.ZERO, PlayedState.Played, description = null)),
)

/** Frame 32 opens the same screen for an episode of a show from search: Play only. */
@PreviewLightDark
@Composable
private fun EpisodeFromSearchPreview() {
    val episode = partOfTheDaily(Duration.ZERO, PlayedState.Unplayed)
    val found = DirectoryEpisode(
        guid = null,
        title = episode.title,
        publishedAt = Instant.parse("2025-03-02T12:00:00Z"),
        length = 72.minutes,
        description = episode.description,
        audioUrl = episode.audioUrl,
    )
    val show = DirectoryShow(TheDaily.directoryId, TheDaily.title, "NY Times", "https://example.com/the-daily.xml")
    EpisodePreview(foundEpisodeContent(show, found))
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
