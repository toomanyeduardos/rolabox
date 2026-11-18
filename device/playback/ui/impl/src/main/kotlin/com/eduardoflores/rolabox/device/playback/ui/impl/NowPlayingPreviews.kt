@file:Suppress("TooManyFunctions") // One for each state of the screen (ADR-016).

package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.compose.runtime.Composable
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.sampleCoverArt
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// The screen as the design shows it (player-designs/device, frames 06 to 08, 20, 21 and 27), on the assembled device.
// They call the stateless composable with explicit state (ADR-016, rule 3).

private val Glasshouse = NowPlayingUiState.Playing(
    title = "Glasshouse",
    artist = "Maren Holt",
    album = "Paper Satellites",
    number = 3,
    count = 10,
    position = 1.minutes + 12.seconds,
    duration = 3.minutes + 41.seconds,
)

private val LongTitle = Glasshouse.copy(
    title = "The Unreasonably Long Name of a Song That Will Never Fit on a Small Display",
    artist = "Maren Holt and the Quiet Fleet Orchestra of Northbound",
    album = "Paper Satellites: The Complete Recordings, Remastered and Expanded",
)

// The design system's sample cover, as the library would hand it over.
private val SampleCover: ArtworkImage = sampleCoverArt().let { image ->
    val pixels = IntArray(image.width * image.height)
    image.readPixels(pixels)
    ArtworkImage(image.width, image.height, pixels)
}

private val WithArtwork = Glasshouse.copy(artwork = SampleCover)

private val Scrubbing = Glasshouse.copy(position = 1.minutes + 48.seconds, mode = NowPlayingMode.Scrub)

private val ChangingVolume = Glasshouse.copy(mode = NowPlayingMode.Volume, volume = 62)

private val Skipped = Glasshouse.copy(
    title = "Tin Can Summer",
    number = 4,
    position = 2.seconds,
    skipped = SkippedSongs("Glasshouse", others = 2, SkipReason.Damaged),
)

@Composable
private fun DevicePreview(state: NowPlayingUiState) {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(state)
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingPreview() = DevicePreview(Glasshouse)

@PreviewLightDark
@Composable
private fun NowPlayingLongTitlePreview() = DevicePreview(LongTitle)

// The song's cover in the placeholder's place, shrinking the same way at 1.5x (ADR-026).
@PreviewLightDark
@Composable
private fun NowPlayingArtworkPreview() = DevicePreview(WithArtwork)

@PreviewLightDark
@Composable
private fun NowPlayingArtworkLongTitlePreview() = DevicePreview(LongTitle.copy(artwork = SampleCover))

@PreviewLightDark
@Composable
private fun NowPlayingNothingLoadedPreview() = DevicePreview(NowPlayingUiState.NothingLoaded)

@PreviewLightDark
@Composable
private fun NowPlayingScrubPreview() = DevicePreview(Scrubbing)

@PreviewLightDark
@Composable
private fun NowPlayingVolumePreview() = DevicePreview(ChangingVolume)

// Frame 20.
@PreviewLightDark
@Composable
private fun NowPlayingSkippedPreview() = DevicePreview(Skipped)

@PreviewLightDark
@Composable
private fun NowPlayingSkippedLongTitlePreview() = DevicePreview(
    Skipped.copy(skipped = SkippedSongs(LongTitle.title, others = 0, SkipReason.Unsupported)),
)

// Frame 21.
@PreviewLightDark
@Composable
private fun NowPlayingNothingLeftToPlayPreview() = DevicePreview(
    NowPlayingUiState.NothingLeftToPlay(SkippedSongs("Glasshouse", others = 2, SkipReason.Damaged)),
)

// Shuffle and Repeat All, as frame 22 of the designs shows them (ADR-028).
@PreviewLightDark
@Composable
private fun NowPlayingShuffleRepeatAllPreview() = DevicePreview(
    Glasshouse.copy(number = 4, shuffled = true, repeat = RepeatMode.All),
)

// Repeat One with Shuffle off, as frame 23 shows it.
@PreviewLightDark
@Composable
private fun NowPlayingRepeatOnePreview() = DevicePreview(Glasshouse.copy(repeat = RepeatMode.One))

// The widest the row gets: a whole library's place, both icons and the volume's tag. At 1.5x the place gives way.
@PreviewLightDark
@Composable
private fun NowPlayingQueueIconsWithVolumePreview() = DevicePreview(
    ChangingVolume.copy(number = 1234, count = 12345, shuffled = true, repeat = RepeatMode.One),
)

// An episode, as frame 27 shows it: its title, its show and its owner, and no place in a queue or icons of one, even
// with Shuffle and Repeat on (ADR-031). At 1.5x the artwork is smaller, above the text, and the title wraps.
@PreviewLightDark
@Composable
private fun NowPlayingEpisodePreview() = DevicePreview(
    NowPlayingUiState.Playing(
        title = "The Strike That Shut Down the Ports",
        artist = "The Daily",
        album = "NY Times",
        number = 1,
        count = 1,
        position = 12.minutes + 4.seconds,
        duration = 32.minutes + 35.seconds,
        artwork = SampleCover,
        shuffled = true,
        repeat = RepeatMode.All,
        isEpisode = true,
    ),
)

/** An episode that plays while it downloads: the lighter part of the bar is what is on the phone (frame 28). */
private val Downloading = NowPlayingUiState.Playing(
    title = "Inside the Race for a Cheaper Battery",
    artist = "The Daily",
    album = "NY Times",
    number = 1,
    count = 1,
    position = 1.minutes + 52.seconds,
    duration = 29.minutes + 32.seconds,
    artwork = SampleCover,
    isEpisode = true,
    downloaded = 0.38f,
    tag = EpisodeTag.Downloading(percent = 38),
)

@PreviewLightDark
@Composable
private fun NowPlayingDownloadingPreview() = DevicePreview(Downloading)

/** It played to the end of what was downloaded, and Use Mobile Data is off on mobile data (frame 37). */
@PreviewLightDark
@Composable
private fun NowPlayingWaitingForWifiPreview() = DevicePreview(
    Downloading.copy(position = 6.minutes + 10.seconds, tag = EpisodeTag.WaitingForWifi),
)

@PreviewLightDark
@Composable
private fun NowPlayingDownloadFailedPreview() = DevicePreview(
    Downloading.copy(downloaded = null, tag = null, downloadFailed = true),
)
