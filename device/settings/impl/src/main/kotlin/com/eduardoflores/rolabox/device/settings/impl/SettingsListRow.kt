package com.eduardoflores.rolabox.device.settings.impl

import com.eduardoflores.rolabox.device.settings.api.SettingsRow

/**
 * A row of the settings list: Settings' own rows, in the order they are shown, and then the
 * contributed sections in the order of their slots (ADR-020, rule 16).
 */
internal sealed interface SettingsListRow {
    data object Clicker : SettingsListRow

    data object Theme : SettingsListRow

    data object MainMenu : SettingsListRow

    data object About : SettingsListRow

    data class Section(val row: SettingsRow) : SettingsListRow
}

internal fun settingsListRows(sections: List<SettingsRow>): List<SettingsListRow> =
    listOf(SettingsListRow.Clicker, SettingsListRow.Theme, SettingsListRow.MainMenu, SettingsListRow.About) +
        sections.map(SettingsListRow::Section)
