package com.eduardoflores.rolabox.core.sync.di

import com.eduardoflores.rolabox.core.sync.NoOpSyncManager
import com.eduardoflores.rolabox.core.sync.SyncManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Same name as the offline module, so @TestInstallIn(replaces = [SyncModule::class]) works in both flavors.
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    // No-op until there is data to sync; the cloud implementation replaces it here.
    @Binds
    internal abstract fun bindsSyncManager(syncManager: NoOpSyncManager): SyncManager
}
