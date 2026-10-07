package com.eduardoflores.rolabox.auth.settings.impl

import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The account section of the device's settings (ADR-020). Signed out, which includes using the app
 * without an account (ADR-008), its row says "Sign in" and opens Sign in, a key of auth's own area.
 * Signed in, the row says who is signed in and opens nothing: the screens for managing an account
 * don't exist yet.
 */
internal class AccountSettingsSection @Inject constructor(private val authRepository: AuthRepository) :
    SettingsSection {
    override val slot = SettingsSlot.Account

    override fun observeRow(): Flow<SettingsRow> = authRepository.observeAuthState().map { state ->
        when (state) {
            AuthState.SignedOut -> SettingsRow(
                title = { stringResource(R.string.auth_settings_account) },
                detail = { stringResource(R.string.auth_settings_sign_in) },
                opens = SignInKey,
            )

            is AuthState.SignedIn -> SettingsRow(
                title = { stringResource(R.string.auth_settings_account) },
                detail = {
                    state.user.displayName?.let { stringResource(R.string.auth_settings_signed_in_as, it) }
                        ?: stringResource(R.string.auth_settings_signed_in)
                },
            )
        }
    }

    // Sign in's entry is added by the auth screens' own contract, so there is nothing to add here.
    override fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit) = Unit
}
