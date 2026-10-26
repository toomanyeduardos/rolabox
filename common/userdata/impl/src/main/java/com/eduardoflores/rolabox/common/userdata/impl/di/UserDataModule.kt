package com.eduardoflores.rolabox.common.userdata.impl.di

import com.eduardoflores.rolabox.common.sync.api.Syncer
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.userdata.impl.DefaultUserDataRepository
import com.eduardoflores.rolabox.common.userdata.impl.PreferencesSyncer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Binds the part's `:api`. Instrumented tests replace this module to use `FakeUserDataRepository`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserDataModule {
    @Binds
    internal abstract fun bindsUserDataRepository(repository: DefaultUserDataRepository): UserDataRepository
}

/**
 * Adds the preferences to what sync runs (ADR-022). It's apart from [UserDataModule] so that a test
 * that replaces the repository keeps the `Syncer` in the graph.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesSyncerModule {
    @Binds
    @IntoSet
    internal abstract fun bindsPreferencesSyncer(syncer: PreferencesSyncer): Syncer
}
