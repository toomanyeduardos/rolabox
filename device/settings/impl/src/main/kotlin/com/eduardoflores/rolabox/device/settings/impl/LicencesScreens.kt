package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListAndText
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.rememberListHighlight
import com.eduardoflores.rolabox.device.settings.api.LicenceKey

/**
 * Open-Source Licences, a device screen: the libraries the app ships, by name (launch/06). Turning moves the
 * highlight, which is saved with the entry (ADR-018, rule 7), and center on a library reports [onLicenceClick] with
 * its key. MENU, the host's, goes back to About.
 */
@Composable
internal fun LicencesRoute(viewModel: LicencesViewModel, onLicenceClick: (LicenceKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlight = rememberListHighlight()
    val libraries = (state as? LicencesUiState.Loaded)?.value.orEmpty()
    ScreenTitle(stringResource(R.string.settings_about_licences))
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, libraries.size)
            WheelEvent.Center -> libraries.getOrNull(highlight.index)?.let { onLicenceClick(LicenceKey(it.id)) }
            else -> Unit
        }
    }
    LicencesList(state, highlightedIndex = highlight.index)
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun LicencesList(state: LicencesUiState<List<Licence>>, highlightedIndex: Int) {
    when (state) {
        // The list is read from the app's resources, so this is a frame. Nothing to say until it is slow.
        LicencesUiState.Loading -> Unit

        is LicencesUiState.Loaded -> DeviceList(
            rows = state.value.map { DeviceListRow(it.name, opensSubmenu = true) },
            highlightedIndex = highlightedIndex,
        )

        LicencesUiState.Unavailable -> DeviceMessage(stringResource(R.string.settings_licences_unavailable))
    }
}

/**
 * A library's licence, a device screen: the licence's name and the library's version, then the licence's text,
 * which the wheel scrolls a line a step. The title is the library's name. MENU, the host's, goes back to the list.
 */
@Composable
internal fun LicenceRoute(viewModel: LicenceViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var scrolledLines by rememberSaveable { mutableIntStateOf(0) }
    var scrollableLines by remember { mutableIntStateOf(0) }
    ScreenTitle((state as? LicencesUiState.Loaded)?.value?.name)
    HandleWheelEvents { event ->
        if (event is WheelEvent.Turn) scrolledLines = (scrolledLines + event.steps).coerceIn(0, scrollableLines)
    }
    LicenceText(
        state = state,
        scrolledLines = scrolledLines,
        onScrollableLinesChange = { lines ->
            scrollableLines = lines
            // A larger font leaves fewer lines to scroll: the text stays at its end.
            scrolledLines = scrolledLines.coerceAtMost(lines)
        },
    )
}

/** The stateless licence that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun LicenceText(state: LicencesUiState<Licence>, scrolledLines: Int, onScrollableLinesChange: (Int) -> Unit) {
    when (state) {
        LicencesUiState.Loading -> Unit

        is LicencesUiState.Loaded -> {
            val licence = state.value
            DeviceListAndText(
                rows = emptyList(),
                highlightedIndex = NO_HIGHLIGHT,
                meta = listOfNotNull(licence.licenceName.ifBlank { null }, licence.version).joinToString(" · "),
                text = licence.text,
                scrolledLines = scrolledLines,
                onScrollableLinesChange = onScrollableLinesChange,
            )
        }

        LicencesUiState.Unavailable -> DeviceMessage(stringResource(R.string.settings_licences_unavailable))
    }
}

private const val NO_HIGHLIGHT = -1

private val PreviewLicences = listOf(
    Licence("androidx.activity:activity", "Activity", "1.13.0", "The Apache License, Version 2.0", null),
    Licence("com.google.re2j:re2j", "RE2/J", "1.8", "Go License", null),
    Licence("androidx.media3:media3-exoplayer", "Media3 ExoPlayer", "1.11.1", "The Apache License, Version 2.0", null),
)

private const val PREVIEW_TEXT =
    "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in " +
        "compliance with the License. You may obtain a copy of the License at\n\n" +
        "https://www.apache.org/licenses/LICENSE-2.0\n\n" +
        "Unless required by applicable law or agreed to in writing, software distributed under the License is " +
        "distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. " +
        "See the License for the specific language governing permissions and limitations under the License."

@PreviewLightDark
@Composable
private fun LicencesPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Open-Source Licences", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                LicencesList(LicencesUiState.Loaded(PreviewLicences), highlightedIndex = 0)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LicencesUnavailablePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Open-Source Licences", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                LicencesList(LicencesUiState.Unavailable, highlightedIndex = 0)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LicencePreview() {
    RolaboxTheme {
        DeviceScreen(title = "Activity", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                LicenceText(
                    LicencesUiState.Loaded(PreviewLicences.first().copy(text = PREVIEW_TEXT)),
                    scrolledLines = 0,
                    onScrollableLinesChange = {},
                )
            }
        }
    }
}
