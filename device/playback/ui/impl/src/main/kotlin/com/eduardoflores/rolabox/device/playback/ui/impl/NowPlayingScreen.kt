package com.eduardoflores.rolabox.device.playback.ui.impl

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceCoverArt
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceNotice
import com.eduardoflores.rolabox.common.designsystem.component.DeviceProgressBar
import com.eduardoflores.rolabox.common.designsystem.component.DeviceQueueIcon
import com.eduardoflores.rolabox.common.designsystem.component.DeviceQueueIcons
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.ProgressBarMode
import com.eduardoflores.rolabox.common.designsystem.component.sampleCoverArt
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleMenu
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ObservePlaybackButtons
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val ScreenPadding = 12.dp
private val ScreenTopPadding = 10.dp
private val SectionGap = 12.dp
private val TextGap = 4.dp
private val CoverMinSize = 56.dp

// Between the place in the queue and its icons, as the designs space them.
private val QueueIconsGap = 8.dp
private const val TITLE_MAX_LINES = 2

/**
 * Connects Now Playing to its ViewModel, which the entry creates (ADR-021). Turn and center go to the
 * ViewModel, and MENU too while scrubbing, so it leaves scrub and the host doesn't go back a screen
 * (ADR-018, rule 16). While scrubbing it is also told of the playback buttons, which leave scrub and then do
 * what they do. The ViewModel is also told when the app goes to the background, with the system's
 * back or with Home, which ends scrub: the screen takes no back of its own (ADR-018, rule 9).
 */
@Composable
internal fun NowPlayingRoute(viewModel: NowPlayingViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenTitle(stringResource(R.string.now_playing_title))
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> viewModel.onTurn(event.steps)
            WheelEvent.Center -> viewModel.onCenter()
            else -> Unit
        }
    }
    val scrubbing = (state as? NowPlayingUiState.Playing)?.mode == NowPlayingMode.Scrub
    HandleMenu(enabled = scrubbing, onMenu = viewModel::onMenu)
    ObservePlaybackButtons(enabled = scrubbing, onPlaybackButton = viewModel::onPlaybackButton)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    NowPlayingScreen(state)
}

/** The stateless screen that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun NowPlayingScreen(state: NowPlayingUiState) {
    when (state) {
        // The playback state is in memory, so this is a frame. Nothing to say until it is slow.
        NowPlayingUiState.Loading -> Unit

        NowPlayingUiState.NothingLoaded -> DeviceMessage(stringResource(R.string.now_playing_nothing_loaded))

        is NowPlayingUiState.NothingLeftToPlay -> DeviceMessage(
            title = stringResource(R.string.now_playing_nothing_left_to_play),
            body = skippedText(state.skipped),
        )

        is NowPlayingUiState.Playing -> NowPlayingContent(state)
    }
}

/** "Skipped Glasshouse", or "Skipped Glasshouse and 2 more" after a run of them. */
@Composable
private fun skippedText(skipped: SkippedSongs): String {
    val title = skipped.title ?: stringResource(R.string.now_playing_unknown_title)
    return if (skipped.others > 0) {
        stringResource(R.string.now_playing_skipped_and_more, title, skipped.others)
    } else {
        stringResource(R.string.now_playing_skipped, title)
    }
}

@Composable
private fun SkipReason.text(): String = stringResource(
    when (this) {
        SkipReason.Missing -> R.string.now_playing_skip_missing
        SkipReason.Damaged -> R.string.now_playing_skip_damaged
        SkipReason.Unsupported -> R.string.now_playing_skip_unsupported
        SkipReason.Protected -> R.string.now_playing_skip_protected
        SkipReason.Unreadable -> R.string.now_playing_skip_unreadable
        SkipReason.Refused -> R.string.now_playing_skip_refused
        SkipReason.Unknown -> R.string.now_playing_skip_unknown
    },
)

/**
 * The song on the display, top to bottom: its place in the queue, its cover art, its title, artist and
 * album, and the bar with the time. It can't scroll, since the wheel is its volume (ADR-018), so at large
 * font scales the text keeps its space and the cover art shrinks to what is left, down to a minimum.
 * The long lines of the song end in an ellipsis, so they never push the bar off the display.
 */
@Composable
private fun NowPlayingContent(state: NowPlayingUiState.Playing) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = ScreenPadding, end = ScreenPadding, top = ScreenTopPadding, bottom = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The icons follow the place in the queue, since both describe the queue (ADR-028). At large font scales
            // the place gives way first, so the icons and the mode always fit. An episode is a queue of its own, with
            // neither (ADR-031, frame 27).
            Row(
                Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(QueueIconsGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.isEpisode) {
                    Text(
                        stringResource(R.string.now_playing_position, state.number, state.count),
                        style = RolaboxType.styles.displayMeta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    DeviceQueueIcons(state.queueIcons)
                }
            }
            when (state.mode) {
                NowPlayingMode.Time -> state.tag?.let { ModeTag(it.text()) }

                NowPlayingMode.Scrub -> ModeTag(
                    stringResource(R.string.now_playing_tag_scrub),
                    RolaboxMetal.colors.accent,
                )

                NowPlayingMode.Volume -> ModeTag(stringResource(R.string.now_playing_tag_volume))
            }
        }
        // Takes what the meta row, the names and the bar leave. The cover art is the largest square that fits in it,
        // so it grows with the display and the names keep their space.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val skipped = state.skipped
            if (skipped != null) {
                // What was skipped takes the cover art's place for a moment, so nothing else on the display moves.
                DeviceNotice(skippedText(skipped), skipped.reason.text(), Modifier.fillMaxSize())
            } else if (state.downloadFailed) {
                DeviceNotice(
                    stringResource(R.string.now_playing_download_failed),
                    stringResource(R.string.now_playing_download_failed_body),
                    Modifier.fillMaxSize(),
                )
            } else {
                DeviceCoverArt(
                    rememberCoverArt(state.artwork),
                    Modifier.size(minOf(maxWidth, maxHeight).coerceAtLeast(CoverMinSize)),
                )
            }
        }
        SongNames(state, Modifier.fillMaxWidth())
        NowPlayingBar(state)
    }
}

/** The cover as Compose draws it, made once for each cover: the state keeps the same one while the song plays. */
@Composable
private fun rememberCoverArt(artwork: ArtworkImage?): ImageBitmap? = remember(artwork) {
    artwork?.let { Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888).asImageBitmap() }
}

/** The bar: the time, and the volume while it is changing. Scrub is the marker's time with a thicker, orange bar. */
@Composable
private fun NowPlayingBar(state: NowPlayingUiState.Playing) {
    if (state.mode == NowPlayingMode.Volume) {
        DeviceProgressBar(
            progress = state.volumeFraction,
            startLabel = stringResource(R.string.now_playing_volume_label),
            endLabel = stringResource(R.string.now_playing_volume_level, state.volume),
            description = stringResource(R.string.now_playing_volume_description),
            mode = ProgressBarMode.Volume,
        )
    } else {
        DeviceProgressBar(
            progress = state.progress,
            startLabel = state.position.toClock(),
            endLabel = timeLeftClock(state.position, state.duration),
            description = stringResource(R.string.now_playing_progress_description),
            mode = if (state.mode == NowPlayingMode.Scrub) ProgressBarMode.Scrub else ProgressBarMode.Normal,
            downloaded = state.downloaded,
        )
    }
}

@Composable
private fun EpisodeTag.text(): String = when (this) {
    is EpisodeTag.Downloading -> stringResource(R.string.now_playing_tag_downloading, percent)
    EpisodeTag.WaitingForWifi -> stringResource(R.string.now_playing_tag_waiting_for_wifi)
    EpisodeTag.WaitingForConnection -> stringResource(R.string.now_playing_tag_no_connection)
}

@Composable
private fun ModeTag(text: String, color: Color = Color.Unspecified) {
    Text(text, style = RolaboxType.styles.displayMeta, color = color)
}

/** The song's title, artist and album. Each one ends in an ellipsis when it is longer than its lines. */
@Composable
private fun SongNames(state: NowPlayingUiState.Playing, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(TextGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.title ?: stringResource(R.string.now_playing_unknown_title),
            style = RolaboxType.styles.displayHeadline,
            textAlign = TextAlign.Center,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            state.artist ?: stringResource(R.string.now_playing_unknown_artist),
            style = RolaboxType.styles.displayBody,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            state.album ?: stringResource(R.string.now_playing_unknown_album),
            style = RolaboxType.styles.displayCaption,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The icons of how the queue plays: Shuffle while it is shuffled, and Repeat or Repeat One (ADR-028). */
private val NowPlayingUiState.Playing.queueIcons: List<DeviceQueueIcon>
    get() = listOfNotNull(
        DeviceQueueIcon.Shuffle.takeIf { shuffled },
        when (repeat) {
            RepeatMode.Off -> null
            RepeatMode.One -> DeviceQueueIcon.RepeatOne
            RepeatMode.All -> DeviceQueueIcon.Repeat
        },
    )

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

@PreviewLightDark
@Composable
private fun NowPlayingPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Glasshouse)
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingLongTitlePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(LongTitle)
        }
    }
}

// The song's cover in the placeholder's place, shrinking the same way at 1.5x (ADR-026).
@PreviewLightDark
@Composable
private fun NowPlayingArtworkPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(WithArtwork)
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingArtworkLongTitlePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(LongTitle.copy(artwork = SampleCover))
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingNothingLoadedPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(NowPlayingUiState.NothingLoaded)
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingScrubPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Scrubbing)
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingVolumePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(ChangingVolume)
        }
    }
}

// Frame 20.
@PreviewLightDark
@Composable
private fun NowPlayingSkippedPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Skipped)
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingSkippedLongTitlePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Skipped.copy(skipped = SkippedSongs(LongTitle.title, others = 0, SkipReason.Unsupported)))
        }
    }
}

// Frame 21.
@PreviewLightDark
@Composable
private fun NowPlayingNothingLeftToPlayPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(
                NowPlayingUiState.NothingLeftToPlay(SkippedSongs("Glasshouse", others = 2, SkipReason.Damaged)),
            )
        }
    }
}

// Shuffle and Repeat All, as frame 22 of the designs shows them (ADR-028).
@PreviewLightDark
@Composable
private fun NowPlayingShuffleRepeatAllPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Glasshouse.copy(number = 4, shuffled = true, repeat = RepeatMode.All))
        }
    }
}

// Repeat One with Shuffle off, as frame 23 shows it.
@PreviewLightDark
@Composable
private fun NowPlayingRepeatOnePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Glasshouse.copy(repeat = RepeatMode.One))
        }
    }
}

// The widest the row gets: a whole library's place, both icons and the volume's tag. At 1.5x the place gives way.
@PreviewLightDark
@Composable
private fun NowPlayingQueueIconsWithVolumePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(
                ChangingVolume.copy(number = 1234, count = 12345, shuffled = true, repeat = RepeatMode.One),
            )
        }
    }
}

// An episode, as frame 27 shows it: its title, its show and its owner, and no place in a queue or icons of one, even
// with Shuffle and Repeat on (ADR-031). At 1.5x the artwork is smaller, above the text, and the title wraps.
@PreviewLightDark
@Composable
private fun NowPlayingEpisodePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(
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
        }
    }
}

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
private fun NowPlayingDownloadingPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Downloading)
        }
    }
}

/** It played to the end of what was downloaded, and Use Mobile Data is off on mobile data (frame 37). */
@PreviewLightDark
@Composable
private fun NowPlayingWaitingForWifiPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Downloading.copy(position = 6.minutes + 10.seconds, tag = EpisodeTag.WaitingForWifi))
        }
    }
}

@PreviewLightDark
@Composable
private fun NowPlayingDownloadFailedPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Now Playing", batteryLevel = 0.7f, onWheelEvent = {}) {
            NowPlayingScreen(Downloading.copy(downloaded = null, tag = null, downloadFailed = true))
        }
    }
}
