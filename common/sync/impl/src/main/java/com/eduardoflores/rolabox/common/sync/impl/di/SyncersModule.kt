package com.eduardoflores.rolabox.common.sync.impl.di

import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.impl.DefaultLastWriteWins
import com.eduardoflores.rolabox.common.sync.impl.FirestoreRemotePreferences
import com.eduardoflores.rolabox.common.sync.impl.PreferencesSyncer
import com.eduardoflores.rolabox.common.sync.impl.RemotePreferences
import com.eduardoflores.rolabox.common.sync.impl.Syncer
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * What a sync run needs. It's apart from [SyncModule] because the worker's entry point is always in
 * the graph, even when a test replaces the `SyncRepository`. Firestore is only created when a sync
 * runs (ADR-008 rule 8), so having this in a test graph creates nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncersModule {
    @Binds
    internal abstract fun bindsRemotePreferences(remote: FirestoreRemotePreferences): RemotePreferences

    // Also injected by the parts that store synced data, to apply what a sync brings (ADR-011).
    @Binds
    internal abstract fun bindsLastWriteWins(lastWriteWins: DefaultLastWriteWins): LastWriteWins

    // Each kind of synced data adds its Syncer to this set (ADR-011).
    @Binds
    @IntoSet
    internal abstract fun bindsPreferencesSyncer(syncer: PreferencesSyncer): Syncer

    companion object {
        @Provides
        fun providesFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    }
}
