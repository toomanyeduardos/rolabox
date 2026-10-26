package com.eduardoflores.rolabox.common.userdata.local.impl.di

import com.eduardoflores.rolabox.common.userdata.local.api.PreferencesLocalDataSource
import com.eduardoflores.rolabox.common.userdata.local.impl.DefaultPreferencesLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesLocalModule {
    @Binds
    internal abstract fun bindsPreferencesLocalDataSource(
        dataSource: DefaultPreferencesLocalDataSource,
    ): PreferencesLocalDataSource
}
