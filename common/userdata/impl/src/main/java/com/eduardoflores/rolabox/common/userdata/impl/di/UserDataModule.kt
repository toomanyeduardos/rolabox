package com.eduardoflores.rolabox.common.userdata.impl.di

import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferencesRepository
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.userdata.impl.DefaultUserDataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UserDataModule {
    @Binds
    internal abstract fun bindsUserDataRepository(repository: DefaultUserDataRepository): UserDataRepository

    @Binds
    internal abstract fun bindsSyncedPreferencesRepository(
        repository: DefaultUserDataRepository,
    ): SyncedPreferencesRepository
}
