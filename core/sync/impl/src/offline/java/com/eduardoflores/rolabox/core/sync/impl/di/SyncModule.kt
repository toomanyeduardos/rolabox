package com.eduardoflores.rolabox.core.sync.impl.di

import com.eduardoflores.rolabox.core.sync.api.SyncRepository
import com.eduardoflores.rolabox.core.sync.impl.NoOpSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    internal abstract fun bindsSyncRepository(syncRepository: NoOpSyncRepository): SyncRepository
}
