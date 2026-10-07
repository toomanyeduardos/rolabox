package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.auth.ui.api.AuthUiEntries
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The real graph, as `:app` assembles it (ADR-020, rule 20). A contribution that is missing doesn't
 * fail the build, so every one that should be there is checked here, and so is that there are no others.
 */
@HiltAndroidTest
class AssembledGraphTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsSections: Set<@JvmSuppressWildcards SettingsSection>

    @Inject lateinit var authRepository: FakeAuthRepository

    @Inject lateinit var authUiEntries: AuthUiEntries

    @Inject lateinit var deviceUiEntries: DeviceUiEntries

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun theSettingsList_hasTheAccountSectionAndNoOther() {
        assertEquals(listOf(SettingsSlot.Account), settingsSections.map { it.slot })
    }

    @Test
    fun theAccountSection_signedOut_opensSignIn() {
        assertEquals(SignInKey, accountRow().opens)
    }

    @Test
    fun theAccountSection_signedIn_opensNothing() {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "1", displayName = "Eduardo", photoUrl = null)))

        assertNull(accountRow().opens)
    }

    private fun accountRow() = runBlocking {
        settingsSections.single { it.slot == SettingsSlot.Account }.observeRow().first()
    }
}
