package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import kotlin.time.Duration
import org.junit.Test

/** A show's episodes for the Play listing and the README (ADR-039). */
class StoreScreenshotTest : StoreScreenshot() {
    @Test
    fun showEpisodes() = show(StoreSet.Play, "08-show-episodes", LongWayRound, LongWayRoundEpisodes, highlighted = 2)

    // The Daily's episodes from Friday 6 November back to Thursday 29 October 2026, newest first, with the
    // highlight on Sunday 1 November's. Played and downloaded as if it had been followed for a while.
    @Test
    fun readmeShowEpisodes() = show(StoreSet.Readme, "show-the-daily", TheDaily, TheDailyEpisodes, highlighted = 6)

    private fun show(set: StoreSet, name: String, show: Show, episodes: List<Episode>, highlighted: Int) =
        shoot(set, name) {
            RolaboxTheme {
                DeviceScreen(title = show.title, batteryLevel = 0.8f, onWheelEvent = {
                }, record = DeviceRecord.Spinning) {
                    Box(Modifier.fillMaxSize()) {
                        val words = ShowWords(
                            follow = stringResource(R.string.podcasts_follow),
                            on = stringResource(R.string.podcasts_on),
                            off = stringResource(R.string.podcasts_off),
                            unknownEpisode = stringResource(R.string.podcasts_unknown_episode),
                        )
                        // The first rows are Follow and Order, so episode n is row n + 1.
                        PodcastsList(
                            ListUiState.Loaded(showRows(ShowContent(show, episodes), words, orderWords())),
                            highlightedIndex = highlighted + 1,
                        )
                    }
                }
            }
        }
}

private val LongWayRound =
    Show(ShowId(1), DirectoryId.itunes(1), "Long Way Round", OwnerId("harbor"), "Harbor Audio", true)

private val LongWayRoundEpisodes = listOf(
    episode(LongWayRound, 1, "Night Ferries of the North Sea", PlayedState.Unplayed, DownloadState.NotDownloaded),
    episode(
        LongWayRound,
        2,
        "The Lighthouse Keepers Who Stayed",
        PlayedState.PartlyPlayed,
        DownloadState.Downloading(0.64f),
    ),
    episode(LongWayRound, 3, "A Railway Through the Clouds", PlayedState.Unplayed, DownloadState.Downloaded),
    episode(LongWayRound, 4, "Salt Roads", PlayedState.Played, DownloadState.Downloaded),
    episode(LongWayRound, 5, "The Last Paper Map Shop", PlayedState.Played, DownloadState.Downloaded),
    episode(LongWayRound, 6, "Walking the Old Border", PlayedState.Played, DownloadState.Downloaded),
)

private val TheDaily = Show(ShowId(2), DirectoryId.itunes(2), "The Daily", OwnerId("nyt"), "The New York Times", true)

private val TheDailyEpisodes = listOf(
    episode(
        TheDaily,
        1,
        "Chuck Schumer on Governing With (Or Without) Trump",
        PlayedState.Unplayed,
        DownloadState.NotDownloaded,
    ),
    episode(TheDaily, 2, "The Voters Who Flipped Congress", PlayedState.Unplayed, DownloadState.NotDownloaded),
    episode(TheDaily, 3, "A Landslide Victory for Democrats", PlayedState.Unplayed, DownloadState.Downloaded),
    episode(TheDaily, 4, "A Guide to Election Night 2026", PlayedState.Played, DownloadState.Downloaded),
    episode(
        TheDaily,
        5,
        "A Political Rebellion in the Rio Grande Valley",
        PlayedState.Played,
        DownloadState.Downloaded,
    ),
    episode(TheDaily, 6, "Want to Enjoy Music More? Go For a Run.", PlayedState.PartlyPlayed, DownloadState.Downloaded),
    episode(
        TheDaily,
        7,
        "Martin McDonagh Has a Vision for Hope. It's Not for the Faint of Heart.",
        PlayedState.Played,
        DownloadState.Downloaded,
    ),
    episode(
        TheDaily,
        8,
        "The Art of the Pardon: How Trump Turned Clemency Into a Business",
        PlayedState.Played,
        DownloadState.Downloaded,
    ),
    episode(
        TheDaily,
        9,
        "A Plan for Two Dans: How Democrats Manipulated Alaska’s Ballot",
        PlayedState.Played,
        DownloadState.Downloaded,
    ),
)

private fun episode(show: Show, id: Long, title: String, played: PlayedState, download: DownloadState) = Episode(
    id = EpisodeId(show.id.value * EPISODES_PER_SHOW + id),
    showId = show.id,
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

private const val EPISODES_PER_SHOW = 100L
