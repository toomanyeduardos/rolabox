package com.eduardoflores.rolabox.device.settings.api

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow

/**
 * The places of the settings list that another part can fill, in the order they are shown. This
 * list is the order of the settings list (ADR-020, rule 16). A new kind of section is a new slot here.
 */
enum class SettingsSlot {
    /** The user's account. */
    Account,
}

/**
 * A section of the settings list that a part contributes (ADR-020): it is added to a Hilt set, which
 * may be empty. A slot nobody fills isn't shown, and a slot has at most one section.
 */
interface SettingsSection {
    /** The slot this section fills. */
    val slot: SettingsSlot

    /**
     * The section's row in the settings list, and again whenever it changes: the account's row
     * depends on whether the user is signed in. It never fails, so it isn't an `Either` (ADR-007).
     */
    fun observeRow(): Flow<SettingsRow>

    /**
     * Adds the full screens behind the row that no other contract adds. The screens are left
     * through [onBack].
     */
    fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit)
}

/**
 * A row of the settings list. The settings list draws it, so every row looks the same.
 *
 * @param title read where the row is shown, so it can come from the contributor's string resources.
 * @param detail what the row says under its title, such as the state it stands for. Read where the
 * row is shown, like [title].
 * @param opens the full screen the row opens, through the generic exit, or null when the row only
 * shows a state. It is a key of the contributor's own area (ADR-020, rule 17).
 */
class SettingsRow(
    val title: @Composable () -> String,
    val detail: (@Composable () -> String)? = null,
    val opens: NavKey? = null,
)
