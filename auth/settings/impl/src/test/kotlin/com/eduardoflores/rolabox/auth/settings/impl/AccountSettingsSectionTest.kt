package com.eduardoflores.rolabox.auth.settings.impl

import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSettingsSectionTest {
    private val section = AccountSettingsSection()

    @Test
    fun fillsTheAccountSlot() {
        assertEquals(SettingsSlot.Account, section.slot)
    }

    // ADR-020 rule 17: a section opens only keys of its own area.
    @Test
    fun opensSignIn() {
        assertEquals(SignInKey, section.row.opens)
    }
}
