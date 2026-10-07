package com.eduardoflores.rolabox.device.settings.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A section that fills [slot] with a row the test can change, and adds one entry. */
internal class FakeSection(override val slot: SettingsSlot) : SettingsSection {
    val row = MutableStateFlow(SettingsRow(title = { slot.name }, opens = FakeKey))

    override fun observeRow(): Flow<SettingsRow> = row

    override fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit) {
        scope.entry<FakeKey> { }
    }
}

internal data object FakeKey : NavKey
