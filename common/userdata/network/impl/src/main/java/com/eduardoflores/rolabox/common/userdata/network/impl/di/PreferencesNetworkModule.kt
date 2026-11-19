package com.eduardoflores.rolabox.common.userdata.network.impl.di

import com.eduardoflores.rolabox.common.userdata.network.api.PreferencesNetworkDataSource
import com.eduardoflores.rolabox.common.userdata.network.impl.FirestorePreferencesNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Firestore comes from `:common:firestore` (ADR-033), injected lazily and only created when a sync calls the data
 * source (ADR-008 rule 8), so having this in a test graph, or in no-account mode, creates nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesNetworkModule {
    @Binds
    internal abstract fun bindsPreferencesNetworkDataSource(
        dataSource: FirestorePreferencesNetworkDataSource,
    ): PreferencesNetworkDataSource
}
