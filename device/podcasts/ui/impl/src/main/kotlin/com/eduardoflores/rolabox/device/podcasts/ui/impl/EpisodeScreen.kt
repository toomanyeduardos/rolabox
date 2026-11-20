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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListAndText
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
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
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Connects an episode of the library to its ViewModel, which the entry creates (ADR-021). The title is the show's.
 * Turning moves between the rows, then scrolls the description, and center plays the episode as the highlighted row
 * says (frame 26). On Needs Wi-Fi, center is [onAllowMobileData], which opens the podcasts' settings (frame 36).
 */
@Composable
internal fun EpisodeRoute(viewModel: EpisodeViewModel, onPlaying: () -> Unit, onAllowMobileData: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wheel by viewModel.wheel.collectAsStateWithLifecycle()
    val playFailure by viewModel.playFailure.collectAsStateWithLifecycle()
    val currentOnPlaying by rememberUpdatedState(onPlaying)
    val currentOnAllowMobileData by rememberUpdatedState(onAllowMobileData)
    ScreenTitle((state as? ListUiState.Loaded)?.value?.showTitle)
    PlayFailureToast(playFailure, onShow = viewModel::onPlayFailureShown)
    HandleWheelEvents { event ->
        when {
            event is WheelEvent.Turn -> viewModel.onTurn(event.steps)

            event == WheelEvent.Center ->
                viewModel.onCenter(onPlaying = {
                    currentOnPlaying()
                }, onAllowMobileData = { currentOnAllowMobileData() })
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    EpisodeScreen(
        state = state,
        wheel = wheel,
        today = remember(zone) { LocalDate.now(zone) },
        zone = zone,
        onScrollableLinesChange = viewModel::onScrollableLinesChange,
    )
}

/**
 * The stateless Episode screen that previews and screenshot tests call (ADR-021, rule 4), for an episode of the
 * library and one of a show opened from search alike. [today] and [zone] date the episode, which shows its year only
 * when it isn't [today]'s.
 */
@Composable
internal fun EpisodeScreen(
    state: ListUiState<EpisodeContent>,
    wheel: EpisodeWheel,
    today: LocalDate,
    zone: ZoneId,
    onScrollableLinesChange: (Int) -> Unit,
) {
    when (state) {
        // The episode is read from the podcasts' database, so this is a frame. Nothing to say until it is slow.
        ListUiState.Loading -> Unit

        is ListUiState.Loaded -> {
            val content = state.value
            val words = episodeWords()
            val locale = LocalConfiguration.current.locales[0]
            DeviceListAndText(
                rows = content.rows.map { DeviceListRow(words.label(it)) },
                highlightedIndex = wheel.row.coerceIn(0, content.rows.lastIndex.coerceAtLeast(0)),
                meta = infoLine(content.info, words, today, zone, locale),
                text = content.description,
                scrolledLines = wheel.lines,
                onScrollableLinesChange = onScrollableLinesChange,
            )
        }

        ListUiState.Failed -> DeviceMessage(stringResource(R.string.podcasts_unavailable))

        ListUiState.Gone -> DeviceMessage(stringResource(R.string.podcasts_no_longer_here))

        ListUiState.NoConnection,
        ListUiState.FeedUnreadable,
        ListUiState.NotInFeed,
        -> FromSearchMessage(state)
    }
}

/**
 * The screen's words, read once from the resources so that the info line can be built outside composition. The
 * formats are Android's, `%d` and `%s`, and the dates are [DateTimeFormatter] patterns.
 */
internal data class EpisodeWords(
    val play: String,
    val resume: String,
    val playFromStart: String,
    val needsWifi: String,
    val notAvailable: String,
    val dateThisYear: String,
    val dateOtherYear: String,
    val minutes: String,
    val hours: String,
    val hoursMinutes: String,
    val underAMinute: String,
    val timeLeft: String,
) {
    fun label(row: EpisodeRow): String = when (row) {
        EpisodeRow.Play -> play
        EpisodeRow.Resume -> resume
        EpisodeRow.PlayFromStart -> playFromStart
        EpisodeRow.NeedsWifi -> needsWifi
        EpisodeRow.NotAvailable -> notAvailable
    }
}

@Composable
internal fun episodeWords() = EpisodeWords(
    play = stringResource(R.string.podcasts_play),
    resume = stringResource(R.string.podcasts_resume),
    playFromStart = stringResource(R.string.podcasts_play_from_start),
    needsWifi = stringResource(R.string.podcasts_needs_wifi),
    notAvailable = stringResource(R.string.podcasts_not_available),
    dateThisYear = stringResource(R.string.podcasts_date_this_year),
    dateOtherYear = stringResource(R.string.podcasts_date_other_year),
    minutes = stringResource(R.string.podcasts_minutes),
    hours = stringResource(R.string.podcasts_hours),
    hoursMinutes = stringResource(R.string.podcasts_hours_minutes),
    underAMinute = stringResource(R.string.podcasts_under_a_minute),
    timeLeft = stringResource(R.string.podcasts_time_left),
)

/**
 * The line under the rows: `NOV 8 · 32 MIN · 20 MIN LEFT`. The date is in [zone], with its year when it isn't
 * [today]'s. A part that isn't known is left out, and the line is `null` when none is.
 */
internal fun infoLine(info: EpisodeInfo, words: EpisodeWords, today: LocalDate, zone: ZoneId, locale: Locale): String? {
    val date = info.publishedAt?.atZone(zone)?.toLocalDate()?.let { date ->
        val pattern = if (date.year == today.year) words.dateThisYear else words.dateOtherYear
        DateTimeFormatter.ofPattern(pattern, locale).format(date).uppercase(locale)
    }
    val parts = listOfNotNull(
        date,
        info.length?.let { durationText(it, words, locale) },
        info.left?.let { words.timeLeft.format(locale, durationText(it, words, locale)) },
    )
    return parts.joinToString(SEPARATOR).ifEmpty { null }
}

/** A length in whole minutes, never rounded up: 32 MIN, 1 HR 12 MIN, 2 HR, or <1 MIN. */
private fun durationText(duration: Duration, words: EpisodeWords, locale: Locale): String {
    val minutes = duration.inWholeMinutes
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        minutes < 1 -> words.underAMinute
        hours == 0L -> words.minutes.format(locale, minutes)
        rest == 0L -> words.hours.format(locale, hours)
        else -> words.hoursMinutes.format(locale, hours, rest)
    }
}

private const val SEPARATOR = " · "
private const val MINUTES_PER_HOUR = 60

/**
 * A play that failed says why, once, in a toast, and then calls [onShow]: nothing is loaded and Now Playing isn't
 * opened (ADR-018, rule 18). A toast is the system's, drawn over the device.
 */
@Composable
private fun PlayFailureToast(failure: EpisodePlayFailure?, onShow: () -> Unit) {
    val context = LocalContext.current
    val currentOnShow by rememberUpdatedState(onShow)
    LaunchedEffect(failure) {
        val message = when (failure ?: return@LaunchedEffect) {
            EpisodePlayFailure.EpisodeUnavailable -> R.string.podcasts_play_episode_unavailable
            EpisodePlayFailure.PlayerUnavailable -> R.string.podcasts_play_player_unavailable
            EpisodePlayFailure.StorageFull -> R.string.podcasts_play_storage_full
            EpisodePlayFailure.MobileDataOff -> R.string.podcasts_play_mobile_data_off
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        currentOnShow()
    }
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

private val FoundShow =
    DirectoryShow(TheDaily.directoryId, TheDaily.title, "NY Times", "https://example.com/the-daily.xml")

private fun foundEpisode(): DirectoryEpisode {
    val episode = partOfTheDaily(Duration.ZERO, PlayedState.Unplayed)
    return DirectoryEpisode(
        guid = null,
        title = episode.title,
        publishedAt = Instant.parse("2025-03-02T12:00:00Z"),
        length = 72.minutes,
        description = episode.description,
        audioUrl = episode.audioUrl,
    )
}

@Composable
private fun EpisodePreview(content: EpisodeContent, wheel: EpisodeWheel = EpisodeWheel()) {
    RolaboxTheme {
        DeviceScreen(title = content.showTitle, batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                EpisodeScreen(
                    state = ListUiState.Loaded(content),
                    wheel = wheel,
                    today = PreviewToday,
                    zone = ZoneOffset.UTC,
                    onScrollableLinesChange = {},
                )
            }
        }
    }
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
private fun EpisodeFromSearchPreview() = EpisodePreview(foundEpisodeContent(FoundShow, foundEpisode()))

/** An episode from search whose publisher refuses its audio: Not available in place of Play (ADR-031, Search). */
@PreviewLightDark
@Composable
private fun EpisodeFromSearchNotAvailablePreview() =
    EpisodePreview(foundEpisodeContent(FoundShow, foundEpisode(), refused = true))
