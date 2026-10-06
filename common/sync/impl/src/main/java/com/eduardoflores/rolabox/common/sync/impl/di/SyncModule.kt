package com.eduardoflores.rolabox.common.sync.impl.di

import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import com.eduardoflores.rolabox.common.sync.impl.WorkManagerSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the part's `:api`. Instrumented tests replace this module to use `FakeSyncRepository`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    internal abstract fun bindsSyncRepository(syncRepository: WorkManagerSyncRepository): SyncRepository
}
