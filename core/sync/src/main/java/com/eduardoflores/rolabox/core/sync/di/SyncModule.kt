package com.eduardoflores.rolabox.core.sync.di

import com.eduardoflores.rolabox.core.sync.NoOpSyncManager
import com.eduardoflores.rolabox.core.sync.SyncManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    internal abstract fun bindsSyncManager(syncManager: NoOpSyncManager): SyncManager
}
