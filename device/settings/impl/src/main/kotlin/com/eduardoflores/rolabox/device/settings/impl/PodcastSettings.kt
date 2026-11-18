package com.eduardoflores.rolabox.device.settings.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.common.userdata.api.EpisodesOnMobileData
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.ScreenTitle
import com.eduardoflores.rolabox.device.host.rememberListHighlight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Podcasts, a device screen of the settings (ADR-031, frame 35): Use Mobile Data, On or Off, changed in place by
 * center. It is a list so later podcast settings have a home. The highlight stays on its one row.
 */
@Composable
internal fun PodcastSettingsRoute(viewModel: PodcastSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlight = rememberListHighlight()
    ScreenTitle(stringResource(R.string.settings_podcasts))
    SaveFailureToast(saveFailed = state.saveFailed, onShow = viewModel::onSaveFailureShown)
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, PODCAST_SETTINGS_ROWS)
            WheelEvent.Center -> viewModel.onMobileDataChange()
            else -> Unit
        }
    }
    PodcastSettingsList(mobileData = state.mobileData, highlightedIndex = highlight.index)
}

/** The stateless list that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun PodcastSettingsList(mobileData: EpisodesOnMobileData?, highlightedIndex: Int) {
    val value = when (mobileData) {
        EpisodesOnMobileData.ON -> stringResource(R.string.settings_on)
        EpisodesOnMobileData.OFF -> stringResource(R.string.settings_off)
        null -> null
    }
    DeviceList(
        rows = listOf(DeviceListRow(stringResource(R.string.settings_podcasts_mobile_data), value = value)),
        highlightedIndex = highlightedIndex,
    )
}

private const val PODCAST_SETTINGS_ROWS = 1

internal data class PodcastSettingsUiState(
    /** Whether episodes may use mobile data, or null until it has loaded, or if it can't be read. */
    val mobileData: EpisodesOnMobileData? = null,
    /** The last change couldn't be saved, until [PodcastSettingsViewModel.onSaveFailureShown]. */
    val saveFailed: Boolean = false,
)

/** What Podcasts shows, and what center does on it (ADR-021). [PodcastSettingsViewModelImpl] implements it. */
internal abstract class PodcastSettingsViewModel : ViewModel() {
    abstract val uiState: StateFlow<PodcastSettingsUiState>

    /** Center on Use Mobile Data: turns it Off when On, and On when Off. Nothing until it has loaded. */
    abstract fun onMobileDataChange()

    /** The failure to save was shown, so it isn't shown again. */
    abstract fun onSaveFailureShown()
}

/** Use Mobile Data is a preference of this phone, stored by `:common:userdata` and never synced (ADR-011, rule 10). */
@HiltViewModel
internal class PodcastSettingsViewModelImpl @Inject constructor(private val userDataRepository: UserDataRepository) :
    PodcastSettingsViewModel() {
    private val saveFailed = MutableStateFlow(false)

    override val uiState: StateFlow<PodcastSettingsUiState> = combine(
        userDataRepository.observeEpisodesOnMobileData(),
        saveFailed,
    ) { mobileData, saveFailed ->
        PodcastSettingsUiState(mobileData = mobileData.getOrNull(), saveFailed = saveFailed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PodcastSettingsUiState(),
    )

    override fun onMobileDataChange() {
        val toggled = when (uiState.value.mobileData ?: return) {
            EpisodesOnMobileData.ON -> EpisodesOnMobileData.OFF
            EpisodesOnMobileData.OFF -> EpisodesOnMobileData.ON
        }
        viewModelScope.launch {
            userDataRepository.setEpisodesOnMobileData(toggled).onLeft { saveFailed.value = true }
        }
    }

    override fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
