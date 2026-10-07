package com.eduardoflores.rolabox.device.settings.impl

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.eduardoflores.rolabox.device.settings.api.SettingsKey
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// A missing or doubled contribution doesn't fail the build (ADR-020), so the slots are tested here
// with fake sections, and the real graph in :app.
class SettingsSectionsTest {
    @Test
    fun sections_areInTheOrderOfTheirSlots() {
        val sections = SettingsSlot.entries.map(::FakeSection)

        val arranged = arrangeSections(sections.reversed().toSet())

        assertEquals(SettingsSlot.entries, arranged.map { it.slot })
    }

    @Test
    fun aSlotNobodyFills_isLeftOut() {
        assertEquals(emptyList<SettingsSection>(), arrangeSections(emptySet()))
    }

    @Test
    fun twoSectionsInOneSlot_fail() {
        val sections = setOf(FakeSection(SettingsSlot.Account), FakeSection(SettingsSlot.Account))

        val error = assertThrows(IllegalStateException::class.java) { arrangeSections(sections) }

        assertTrue(error.message, error.message!!.contains("Account"))
        assertTrue(error.message, error.message!!.contains("ADR-020 rule 16"))
    }

    @Test
    fun entries_areTheSettingsListAndEachSectionsOwn() {
        val settingsEntries = DefaultSettingsEntries(setOf(FakeSection(SettingsSlot.Account)))

        val provider = entryProvider<NavKey> {
            settingsEntries.appStackEntries(this, onOpenFullScreen = {}, onBack = {})
        }

        // A key with no entry throws.
        provider(SettingsKey)
        provider(FakeKey)
    }
}
