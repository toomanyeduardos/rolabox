package com.eduardoflores.rolabox.common.firestore.di

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The one provider of Firestore in the app (ADR-033, rule 1). Each `:network:impl` injects it through `Lazy`, so it
 * is only created when a sync calls a data source (ADR-008, rule 8), and having this in a test graph, or in
 * no-account mode, creates nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirestoreModule {
    @Provides
    fun providesFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
