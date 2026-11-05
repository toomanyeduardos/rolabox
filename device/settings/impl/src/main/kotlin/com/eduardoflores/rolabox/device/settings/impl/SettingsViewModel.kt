package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.Repeat
import com.eduardoflores.rolabox.common.userdata.api.Shuffle
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import kotlinx.coroutines.flow.StateFlow

internal data class SettingsUiState(
    /** Shuffle (ADR-028), or null until it has loaded, or if it can't be read, like [clicker]. */
    val shuffle: Shuffle? = null,
    /** Repeat (ADR-028), or null until it has loaded, or if it can't be read, like [clicker]. */
    val repeat: Repeat? = null,
    /** Clicker, or null until it has loaded, or if it can't be read: then the row shows no value. */
    val clicker: Clicker? = null,
    /** The theme, or null until it has loaded, or if it can't be read, like [clicker]. */
    val darkTheme: DarkThemeConfig? = null,
    /**
     * Whether covers missing from the phone are downloaded (ADR-027), or null until it has loaded, or if it can't be
     * read, like [clicker].
     */
    val onlineArtwork: Boolean? = null,
    /** The rows of the contributed sections, in the order of their slots. */
    val sections: List<SettingsRow> = emptyList(),
    /** The last change couldn't be saved, until [SettingsViewModel.onSaveFailureShown]. */
    val saveFailed: Boolean = false,
)

/** What the settings list shows, and what the wheel does on it (ADR-021). [SettingsViewModelImpl] implements it. */
internal abstract class SettingsViewModel : ViewModel() {
    abstract val uiState: StateFlow<SettingsUiState>

    /** Center on Shuffle: steps to the next of Off, Songs and Albums, and around. Nothing until it has loaded. */
    abstract fun onShuffleChange()

    /** Center on Repeat: steps to the next of Off, One and All, and around. Nothing until it has loaded. */
    abstract fun onRepeatChange()

    /** Center on Clicker: turns it Off when it is On, and On when it is Off. Nothing until it has loaded. */
    abstract fun onClickerChange()

    /** Center on Theme: steps to the next of System, Light and Dark, and around. Nothing until it has loaded. */
    abstract fun onThemeChange()

    /** Center on Online Artwork: turns it Off when it is On, and On when it is Off. Nothing until it has loaded. */
    abstract fun onOnlineArtworkChange()

    /** The failure to save was shown, so it isn't shown again. */
    abstract fun onSaveFailureShown()
}
