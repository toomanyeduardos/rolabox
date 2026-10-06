package com.eduardoflores.rolabox.device.settings.impl.di

import com.eduardoflores.rolabox.device.settings.api.SettingsEntries
import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.impl.DefaultSettingsEntries
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    internal abstract fun bindsSettingsEntries(entries: DefaultSettingsEntries): SettingsEntries

    // Declared here so the set may be empty: the device is built and shown without any part that
    // contributes a section (ADR-020). A part adds one with @Binds @IntoSet.
    @Multibinds
    abstract fun settingsSections(): Set<SettingsSection>
}
