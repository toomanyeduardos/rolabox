package com.eduardoflores.rolabox.common.sync.impl.di

import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.Syncer
import com.eduardoflores.rolabox.common.sync.impl.DefaultLastWriteWins
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * What a sync run needs. It's apart from [SyncModule] because the worker's entry point is always in
 * the graph, even when a test replaces the `SyncRepository`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncersModule {
    // Also injected by the parts that store synced data, to apply what a sync brings (ADR-011).
    @Binds
    internal abstract fun bindsLastWriteWins(lastWriteWins: DefaultLastWriteWins): LastWriteWins

    // Each part with synced data adds its Syncer to this set from its own :impl (ADR-022). Declared
    // here so that it may be empty.
    @Multibinds
    abstract fun syncers(): Set<Syncer>
}
