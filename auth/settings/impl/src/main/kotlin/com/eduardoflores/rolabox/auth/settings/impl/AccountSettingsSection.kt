package com.eduardoflores.rolabox.auth.settings.impl

import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.auth.ui.api.SignOutKey
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The account section of the device's settings (ADR-020): the Account row, which leaves the display
 * for a full screen of auth's own area (ADR-018, rule 11). Signed out, which includes using the app
 * without an account (ADR-008), it opens Sign in. Signed in, it opens Sign out, where the user
 * confirms leaving.
 */
internal class AccountSettingsSection @Inject constructor(private val authRepository: AuthRepository) :
    SettingsSection {
    override val slot = SettingsSlot.Account

    override fun observeRow(): Flow<SettingsRow> = authRepository.observeAuthState().map { state ->
        SettingsRow(
            title = { stringResource(R.string.auth_settings_account) },
            opens = when (state) {
                AuthState.SignedOut -> SignInKey
                is AuthState.SignedIn -> SignOutKey
            },
        )
    }

    // The entries of Sign in and Sign out are added by the auth screens' own contract, so there is nothing to add here.
    override fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit) = Unit
}
