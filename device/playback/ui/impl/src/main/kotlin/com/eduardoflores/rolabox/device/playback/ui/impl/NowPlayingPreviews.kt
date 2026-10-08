package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.compose.runtime.Composable
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// The screen as the design shows it (player-designs/device, frame 06), on the assembled device. They call
// the stateless composable with explicit state (ADR-016, rule 3).

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

@PreviewLightDark
@Composable
private fun NowPlayingNothingLoadedPreview() = DevicePreview(NowPlayingUiState.NothingLoaded)
