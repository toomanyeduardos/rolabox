package com.eduardoflores.rolabox.device.playback.ui.impl

import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.screenshot.CoverPixels
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.screenshot.colorMixCover
import com.eduardoflores.rolabox.common.designsystem.screenshot.localArtwork
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/** Now Playing for the Play listing and the README (ADR-039). */
class StoreScreenshotTest : StoreScreenshot() {
    @Test
    fun nowPlaying() = nowPlaying(
        StoreSet.Play,
        "04-now-playing",
        NowPlayingUiState.Playing(
            title = "Weather Report",
            artist = "Maren Holt",
            album = "Paper Satellites",
            number = 5,
            count = 10,
            position = 1.minutes + 38.seconds,
            duration = 3.minutes + 52.seconds,
            artwork = colorMixCover(from = 0xFF1B3B6F, to = 0xFFE86A5C, accent = 0xFFFFD27A, seed = 4).toArtwork(),
        ),
    )

    @Test
    fun nowPlayingShuffleAndRepeat() = nowPlaying(
        StoreSet.Play,
        "05-now-playing-shuffle-repeat",
        NowPlayingUiState.Playing(
            title = "Lantern Season",
            artist = "Pale Lanterns",
            album = "Small Hours",
            number = 23,
            count = 148,
            position = 2.minutes + 4.seconds,
            duration = 4.minutes + 17.seconds,
            artwork = colorMixCover(from = 0xFF0F5B4E, to = 0xFF9BD17A, accent = 0xFF2E2A6B, seed = 7).toArtwork(),
            shuffled = true,
            repeat = RepeatMode.All,
        ),
    )

    @Test
    fun nowPlayingEpisode() = nowPlaying(
        StoreSet.Play,
        "09-now-playing-episode",
        NowPlayingUiState.Playing(
            title = "The Lighthouse Keepers Who Stayed",
            artist = "Long Way Round",
            album = "Harbor Audio",
            number = 1,
            count = 1,
            position = 14.minutes + 22.seconds,
            duration = 41.minutes + 8.seconds,
            artwork = colorMixCover(from = 0xFF4A1F5C, to = 0xFFF2A65A, accent = 0xFF7FD3E0, seed = 9).toArtwork(),
            isEpisode = true,
            downloaded = 0.64f,
            tag = EpisodeTag.Downloading(percent = 64),
        ),
    )

    // Track 1 of 16 of U2's The Best of 1990-2000, 0:37 in. The cover is read from .screenshots-local.
    @Test
    fun readmeNowPlaying() = nowPlaying(
        StoreSet.Readme,
        "now-playing-u2",
        NowPlayingUiState.Playing(
            title = "Even Better Than the Real Thing",
            artist = "U2",
            album = "The Best of 1990-2000",
            number = 1,
            count = 16,
            position = 37.seconds,
            duration = 3.minutes + 41.seconds,
            artwork = localArtwork("u2-best-of-1990-2000.jpg").toArtwork(),
        ),
    )

    // The Daily's episode of Sunday 1 November 2026. Its artwork is read from .screenshots-local.
    @Test
    fun readmeNowPlayingEpisode() = nowPlaying(
        StoreSet.Readme,
        "now-playing-the-daily",
        NowPlayingUiState.Playing(
            title = "Want to Enjoy Music More? Go For a Run.",
            artist = "The Daily",
            album = "The New York Times",
            number = 1,
            count = 1,
            position = 14.minutes + 5.seconds,
            duration = 36.minutes + 12.seconds,
            artwork = localArtwork("the-daily-2026-11-01.jpg").toArtwork(),
            isEpisode = true,
            downloaded = 1f,
        ),
    )

    private fun nowPlaying(set: StoreSet, name: String, state: NowPlayingUiState.Playing) = shoot(set, name) {
        RolaboxTheme {
            DeviceScreen(title = "Now Playing", batteryLevel = 0.8f, onWheelEvent = {
            }, record = DeviceRecord.Spinning) {
                NowPlayingScreen(state)
            }
        }
    }
}

private fun CoverPixels.toArtwork() = ArtworkImage(width, height, pixels)
