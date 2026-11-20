package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListEntry
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListSection
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.DeviceTyping
import com.eduardoflores.rolabox.device.host.HandleMenuWithField
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ListHighlight
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.TypingField
import com.eduardoflores.rolabox.device.host.highlightAfterTyping
import com.eduardoflores.rolabox.device.host.isOnField
import com.eduardoflores.rolabox.device.host.moveWithField
import com.eduardoflores.rolabox.device.host.rememberDeviceTyping
import com.eduardoflores.rolabox.device.host.rememberListHighlight
import com.eduardoflores.rolabox.device.host.rowIndex
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryResults
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchEpisodeKey
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchShowKey

/**
 * Connects Search to its ViewModel (ADR-021; ADR-031, Search). It opens typing, with the keyboard up (frame 29), and
 * the field's text goes to the ViewModel as it is typed. When typing ends, the wheel is on the first result, skipping
 * the section labels (frame 31). MENU on a result highlights the field, center on the field types again, and MENU on
 * the field leaves Search (ADR-032, rule 5).
 *
 * Center on a show or an episode opens the library's own screen when the library has it ([onShowClick],
 * [onEpisodeClick]), and otherwise the screen of one from search ([onSearchShowClick], [onSearchEpisodeClick]).
 */
@Composable
internal fun SearchRoute(
    viewModel: SearchViewModel,
    onShowClick: (showId: Long) -> Unit,
    onEpisodeClick: (episodeId: Long) -> Unit,
    onSearchShowClick: (PodcastSearchShowKey) -> Unit,
    onSearchEpisodeClick: (PodcastSearchEpisodeKey) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The screen owns the text and saves it with its entry, and the host whether it is typing (ADR-032, rule 6).
    val text = rememberTextFieldState()
    val typing = rememberDeviceTyping(typeWhenShown = true)
    val highlight = rememberListHighlight()
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(viewModel::onTextChange) }
    val words = searchWords()
    val rows = remember(state, words, typing.isTyping) { searchRows(state, words, typing.isTyping) }
    val entries = rows.map { it.entry }
    // Results that change while the wheel is on them, such as when the connection comes back, keep it on a row.
    LaunchedEffect(entries) { if (!typing.isTyping) highlight.moveWithField(0, entries) }
    ScreenTitle(stringResource(R.string.podcasts_search))
    HandleMenuWithField(highlight)
    HandleWheelEvents { event ->
        when {
            event is WheelEvent.Turn -> highlight.moveWithField(event.steps, entries)

            event != WheelEvent.Center -> Unit

            highlight.isOnField -> typing.start()

            else -> when (val target = rows.getOrNull(highlight.index - 1)?.target) {
                is SearchTarget.Show -> viewModel.onShowClick(
                    target.show,
                    onLibraryShow = onShowClick,
                    onFoundShow = { onSearchShowClick(target.show.toKey()) },
                )

                is SearchTarget.Episode -> viewModel.onEpisodeClick(
                    target.found,
                    onLibraryEpisode = onEpisodeClick,
                    onFoundEpisode = { onSearchEpisodeClick(episodeKeyOf(target.found.show, target.found.episode)) },
                )

                null -> Unit
            }
        }
    }
    SearchScreen(
        text = text,
        typing = typing,
        highlight = highlight,
        entries = entries,
        message = searchMessage(state),
        onTypingEnd = { highlight.highlightAfterTyping(entries) },
        onSearchAction = viewModel::onSearchAction,
    )
}

/**
 * The stateless Search screen that previews and screenshot tests call (ADR-021, rule 4): the field, then the
 * [entries], with the [message] under them. Nothing is highlighted while [typing] (frame 30).
 */
@Composable
internal fun SearchScreen(
    text: TextFieldState,
    typing: DeviceTyping,
    highlight: ListHighlight,
    entries: List<DeviceListEntry>,
    message: DeviceListMessage?,
    onTypingEnd: () -> Unit,
    onSearchAction: () -> Unit,
) {
    DeviceList(rows = entries, highlightedIndex = highlight.rowIndex(typing), message = message) {
        TypingField(
            typing = typing,
            state = text,
            hint = stringResource(R.string.podcasts_search_hint),
            shortHint = stringResource(R.string.podcasts_search_short_hint),
            highlighted = highlight.isOnField,
            onTypingEnd = onTypingEnd,
            onAction = onSearchAction,
        )
    }
}

/** What center on a row of results opens. */
internal sealed interface SearchTarget {
    data class Show(val show: DirectoryShow) : SearchTarget

    data class Episode(val found: FoundEpisode) : SearchTarget
}

/** A row of results, and what center on it opens: `null` for a section label, which the wheel skips. */
internal data class SearchRow(val entry: DeviceListEntry, val target: SearchTarget?)

/** The screen's words, read once from the resources so that the rows can be built outside composition. */
internal data class SearchWords(val shows: String, val episodes: String, val unknownEpisode: String)

@Composable
internal fun searchWords() = SearchWords(
    shows = stringResource(R.string.podcasts_search_shows),
    episodes = stringResource(R.string.podcasts_search_episodes),
    unknownEpisode = stringResource(R.string.podcasts_unknown_episode),
)

/**
 * Shows, with their creator in the row's quieter value, then Episodes, each under its label, and a section with
 * nothing in it left out (frames 30 and 31). An episode's `›` shows once typing ends, as the designs draw it.
 */
internal fun searchRows(state: SearchUiState, words: SearchWords, typing: Boolean): List<SearchRow> {
    val results = (state as? SearchUiState.Found)?.results ?: return emptyList()
    val shows = results.shows.map { show ->
        SearchRow(DeviceListRow(show.title, secondaryValue = show.creator), SearchTarget.Show(show))
    }
    val episodes = results.episodes.map { found ->
        SearchRow(
            DeviceListRow(found.episode.title ?: words.unknownEpisode, opensSubmenu = !typing),
            SearchTarget.Episode(found),
        )
    }
    return section(words.shows, shows) + section(words.episodes, episodes)
}

private fun section(label: String, rows: List<SearchRow>): List<SearchRow> =
    if (rows.isEmpty()) emptyList() else listOf(SearchRow(DeviceListSection(label), target = null)) + rows

/** What the screen says under the field, when it has something to say instead of results (frames 33 and 34). */
@Composable
internal fun searchMessage(state: SearchUiState): DeviceListMessage? {
    val (title, body) = when (state) {
        SearchUiState.Empty, is SearchUiState.Found -> return null
        SearchUiState.NoResults -> R.string.podcasts_search_none_title to R.string.podcasts_search_none_body
        SearchUiState.NoConnection -> R.string.podcasts_search_offline_title to R.string.podcasts_search_offline_body
        SearchUiState.Busy -> R.string.podcasts_search_busy_title to R.string.podcasts_search_busy_body
        SearchUiState.Failed -> R.string.podcasts_search_failed_title to R.string.podcasts_search_failed_body
    }
    return DeviceListMessage(stringResource(title), stringResource(body))
}

private fun found(id: Long, title: String, creator: String) =
    DirectoryShow(DirectoryId.itunes(id), title, creator, "https://example.com/$id.xml")

private const val COMMUTERS_ID = 9L
private const val DAILY_DOSE_ID = 4L
private const val DAILY_BRIEF_ID = 5L

private val FoundTheDaily = found(1, "The Daily", "NY Times")

private fun foundEpisode(title: String) = FoundEpisode(
    show = found(COMMUTERS_ID, "Commuters", "Field Notes Media"),
    episode = DirectoryEpisode(
        guid = title,
        title = title,
        publishedAt = null,
        length = null,
        description = null,
        audioUrl = "https://example.com/${title.length}.mp3",
    ),
)

private val TypingResults = DirectoryResults(
    shows = listOf(FoundTheDaily, found(DAILY_DOSE_ID, "Daily Dose of Design", "Field Notes Media")),
    episodes = listOf(foundEpisode("Why the Daily Commute Changed"), foundEpisode("A Daily Habit, Ten Years Later")),
)

private val Results = TypingResults.copy(
    shows = listOf(FoundTheDaily, found(DAILY_BRIEF_ID, "The Daily Brief", "Harbor Audio"), TypingResults.shows[1]),
)

@Composable
private fun SearchPreview(text: String, state: SearchUiState, typing: Boolean) {
    RolaboxTheme {
        DeviceScreen(title = "Search", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val deviceTyping = rememberDeviceTyping(typeWhenShown = typing)
                val highlight = rememberListHighlight()
                val entries = searchRows(state, searchWords(), typing).map { it.entry }
                if (!typing) highlight.highlightAfterTyping(entries)
                SearchScreen(
                    text = rememberTextFieldState(text),
                    typing = deviceTyping,
                    highlight = highlight,
                    entries = entries,
                    message = searchMessage(state),
                    onTypingEnd = {},
                    onSearchAction = {},
                )
            }
        }
    }
}

/** Frame 29: Search opens typing, with the hint in the empty field. */
@PreviewLightDark
@Composable
private fun SearchEmptyPreview() = SearchPreview("", SearchUiState.Empty, typing = true)

/** Frame 30: results update as the user types, Shows then Episodes, and nothing is highlighted. */
@PreviewLightDark
@Composable
private fun SearchTypingPreview() = SearchPreview("the dai", SearchUiState.Found(TypingResults), typing = true)

/** Frame 31: typing ended, so the wheel is on the first result, past the label. */
@PreviewLightDark
@Composable
private fun SearchResultsPreview() = SearchPreview("the daily", SearchUiState.Found(Results), typing = false)

/** Frame 33: no connection. The text stays. */
@PreviewLightDark
@Composable
private fun SearchOfflinePreview() = SearchPreview("the daily", SearchUiState.NoConnection, typing = true)

/** Frame 34: the directory found nothing. */
@PreviewLightDark
@Composable
private fun SearchNoResultsPreview() = SearchPreview("zzqx radio", SearchUiState.NoResults, typing = true)

/** The directory refused for the rate of searches: its own message, and the text stays. */
@PreviewLightDark
@Composable
private fun SearchBusyPreview() = SearchPreview("the daily", SearchUiState.Busy, typing = false)
