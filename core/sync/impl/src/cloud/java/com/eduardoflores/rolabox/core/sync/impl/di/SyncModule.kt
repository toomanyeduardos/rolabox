package com.eduardoflores.rolabox.core.sync.impl.di

import com.eduardoflores.rolabox.core.sync.api.SyncRepository
import com.eduardoflores.rolabox.core.sync.impl.FirestoreRemotePreferences
import com.eduardoflores.rolabox.core.sync.impl.PreferencesSyncer
import com.eduardoflores.rolabox.core.sync.impl.RemotePreferences
import com.eduardoflores.rolabox.core.sync.impl.Syncer
import com.eduardoflores.rolabox.core.sync.impl.WorkManagerSyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

// Same name as the offline module, so @TestInstallIn(replaces = [SyncModule::class]) works in both flavors.
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    internal abstract fun bindsSyncRepository(syncRepository: WorkManagerSyncRepository): SyncRepository

    @Binds
    internal abstract fun bindsRemotePreferences(remote: FirestoreRemotePreferences): RemotePreferences

    // Each kind of synced data adds its Syncer to this set (ADR-011).
    @Binds
    @IntoSet
    internal abstract fun bindsPreferencesSyncer(syncer: PreferencesSyncer): Syncer

    companion object {
        @Provides
        fun providesFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    }
}
