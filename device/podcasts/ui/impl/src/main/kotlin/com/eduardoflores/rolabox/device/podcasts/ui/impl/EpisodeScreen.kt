package com.eduardoflores.rolabox.device.podcasts.ui.impl

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListAndText
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration

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
    }
}

@Composable
internal fun episodeWords() = EpisodeWords(
    play = stringResource(R.string.podcasts_play),
    resume = stringResource(R.string.podcasts_resume),
    playFromStart = stringResource(R.string.podcasts_play_from_start),
    needsWifi = stringResource(R.string.podcasts_needs_wifi),
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
