package com.eduardoflores.rolabox.device.settings.impl

import com.eduardoflores.rolabox.device.settings.api.SettingsRow

/**
 * A row of the settings list: Settings' own rows, in the order they are shown, and then the
 * contributed sections in the order of their slots (ADR-020, rule 16).
 */
internal sealed interface SettingsListRow {
    data object Shuffle : SettingsListRow

    data object Repeat : SettingsListRow

    data object Clicker : SettingsListRow

    data object Theme : SettingsListRow

    data object OnlineArtwork : SettingsListRow

    data object MainMenu : SettingsListRow

    data object About : SettingsListRow

    data class Section(val row: SettingsRow) : SettingsListRow
}

internal fun settingsListRows(sections: List<SettingsRow>): List<SettingsListRow> = listOf(
    SettingsListRow.Shuffle,
    SettingsListRow.Repeat,
    SettingsListRow.Clicker,
    SettingsListRow.Theme,
    SettingsListRow.OnlineArtwork,
    SettingsListRow.MainMenu,
    SettingsListRow.About,
) +
    sections.map(SettingsListRow::Section)
