package com.eduardoflores.rolabox.auth.settings.impl.di

import com.eduardoflores.rolabox.auth.settings.impl.AccountSettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the account section to the device's settings list. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthSettingsModule {
    @Binds
    @IntoSet
    internal abstract fun bindsAccountSettingsSection(section: AccountSettingsSection): SettingsSection
}
