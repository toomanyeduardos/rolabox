package com.eduardoflores.rolabox.common.userdata.network.impl.di

import com.eduardoflores.rolabox.common.userdata.network.api.PreferencesNetworkDataSource
import com.eduardoflores.rolabox.common.userdata.network.impl.FirestorePreferencesNetworkDataSource
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Firestore is injected lazily and only created when a sync calls the data source (ADR-008 rule 8),
 * so having this in a test graph, or in offline mode, creates nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesNetworkModule {
    @Binds
    internal abstract fun bindsPreferencesNetworkDataSource(
        dataSource: FirestorePreferencesNetworkDataSource,
    ): PreferencesNetworkDataSource

    companion object {
        @Provides
        fun providesFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    }
}
