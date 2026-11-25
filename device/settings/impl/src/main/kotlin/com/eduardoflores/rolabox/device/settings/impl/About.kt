package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.rememberListHighlight

/**
 * About, a device screen: the app's name and version and the number of songs in the library that can be played, which
 * can't be selected, and under them the rows that open something (launch/06, card 96). Turning moves the highlight over
 * those rows only, and it is saved with the entry (ADR-018, rule 7). Center on Made By opens the author's GitHub
 * profile and on Privacy Policy the policy, both in the browser, and on Send Feedback an email to Rolabox in the email
 * app, all through [ExternalApps]. Center on Open-Source Licences reports [onLicencesClick], and on Legal, which says
 * Rolabox isn't Apple's, [onLegalClick]. MENU, the host's, goes back.
 */
@Composable
internal fun AboutRoute(viewModel: AboutViewModel, onLicencesClick: () -> Unit, onLegalClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlight = rememberListHighlight()
    val externalApps = rememberExternalApps()
    val authorUrl = stringResource(R.string.settings_about_author_url)
    val privacyPolicyUrl = stringResource(R.string.settings_about_privacy_policy_url)
    val feedbackAddress = stringResource(R.string.settings_about_feedback_address)
    val feedbackSubject = stringResource(R.string.settings_about_feedback_subject, state.version)
    ScreenTitle(stringResource(R.string.settings_about))
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, AboutAction.entries.size)

            WheelEvent.Center -> when (AboutAction.entries.getOrNull(highlight.index)) {
                AboutAction.Author -> externalApps.openUrl(authorUrl)
                AboutAction.PrivacyPolicy -> externalApps.openUrl(privacyPolicyUrl)
                AboutAction.Licences -> onLicencesClick()
                AboutAction.Legal -> onLegalClick()
                AboutAction.Feedback -> externalApps.sendEmail(feedbackAddress, feedbackSubject)
                null -> Unit
            }

            else -> Unit
        }
    }
    AboutList(state, highlighted = AboutAction.entries.getOrNull(highlight.index))
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun AboutList(state: AboutUiState, highlighted: AboutAction?) {
    val songs = when (val songs = state.songs) {
        SongCount.Loading -> null
        is SongCount.Loaded -> songs.count.toString()
        SongCount.Unavailable -> stringResource(R.string.settings_about_unavailable)
    }
    val info = listOf(
        DeviceListRow(stringResource(R.string.settings_about_app_name)),
        DeviceListRow(stringResource(R.string.settings_about_version), value = state.version),
        DeviceListRow(stringResource(R.string.settings_about_songs), value = songs),
    )
    val actions = AboutAction.entries.map { action ->
        when (action) {
            AboutAction.Author -> DeviceListRow(stringResource(R.string.settings_about_author))
            AboutAction.PrivacyPolicy -> DeviceListRow(stringResource(R.string.settings_about_privacy_policy))
            AboutAction.Licences -> DeviceListRow(stringResource(R.string.settings_about_licences), opensSubmenu = true)
            AboutAction.Legal -> DeviceListRow(stringResource(R.string.settings_about_legal), opensSubmenu = true)
            AboutAction.Feedback -> DeviceListRow(stringResource(R.string.settings_about_feedback))
        }
    }
    DeviceList(
        rows = info + actions,
        highlightedIndex = highlighted?.let { info.size + it.ordinal } ?: NO_HIGHLIGHT,
    )
}

private const val NO_HIGHLIGHT = -1

// The number of songs frame 13 shows.
private const val PREVIEW_SONG_COUNT = 42

@PreviewLightDark
@Composable
private fun AboutPreview() {
    RolaboxTheme {
        DeviceScreen(title = "About", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                AboutList(
                    AboutUiState(version = "1.0", songs = SongCount.Loaded(PREVIEW_SONG_COUNT)),
                    highlighted = AboutAction.PrivacyPolicy,
                )
            }
        }
    }
}
