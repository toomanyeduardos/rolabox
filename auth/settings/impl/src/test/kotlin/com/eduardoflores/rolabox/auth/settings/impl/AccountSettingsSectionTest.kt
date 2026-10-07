package com.eduardoflores.rolabox.auth.settings.impl

import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSettingsSectionTest {
    private val authRepository = FakeAuthRepository()
    private val section = AccountSettingsSection(authRepository)

    @Test
    fun fillsTheAccountSlot() {
        assertEquals(SettingsSlot.Account, section.slot)
    }

    // ADR-020 rule 17: a section opens only keys of its own area.
    @Test
    fun signedOut_theRowOpensSignIn() = runTest {
        assertEquals(SignInKey, section.observeRow().first().opens)
    }

    @Test
    fun signedIn_theRowOpensNothing() = runTest {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "1", displayName = "Eduardo", photoUrl = null)))

        assertNull(section.observeRow().first().opens)
    }

    @Test
    fun theRowFollowsTheAuthState() = runTest {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "1", displayName = null, photoUrl = null)))
        assertNull(section.observeRow().first().opens)

        authRepository.setAuthState(AuthState.SignedOut)
        assertEquals(SignInKey, section.observeRow().first().opens)
    }
}
