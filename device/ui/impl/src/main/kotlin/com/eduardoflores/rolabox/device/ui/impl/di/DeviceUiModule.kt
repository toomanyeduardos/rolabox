package com.eduardoflores.rolabox.device.ui.impl.di

import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import com.eduardoflores.rolabox.device.ui.impl.DefaultDeviceUiEntries
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the part's `:api`: its entry contract, which `:app` injects. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceUiModule {
    @Binds
    internal abstract fun bindsDeviceUiEntries(entries: DefaultDeviceUiEntries): DeviceUiEntries
}
