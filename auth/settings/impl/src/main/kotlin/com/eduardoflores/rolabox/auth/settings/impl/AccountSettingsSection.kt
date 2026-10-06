package com.eduardoflores.rolabox.auth.settings.impl

import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.device.settings.api.SettingsRow
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot
import javax.inject.Inject

/**
 * The account section of the device's settings (ADR-020). Its row opens Sign in, a key of auth's
 * own area. What the section shows for a signed-in user comes with 37.11.
 */
internal class AccountSettingsSection @Inject constructor() : SettingsSection {
    override val slot = SettingsSlot.Account

    override val row = SettingsRow(
        title = { stringResource(R.string.auth_settings_account) },
        opens = SignInKey,
    )

    // Sign in's entry is added by the auth screens' own contract, so there is nothing to add here.
    override fun appStackEntries(scope: EntryProviderScope<NavKey>, onBack: () -> Unit) = Unit
}
