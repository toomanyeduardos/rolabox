package com.eduardoflores.rolabox.core.auth.di

import com.eduardoflores.rolabox.core.auth.FirebaseAuthRepository
import com.eduardoflores.rolabox.core.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Same name as the offline module, so @TestInstallIn(replaces = [AuthModule::class]) works in both flavors.
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    internal abstract fun bindsAuthRepository(repository: FirebaseAuthRepository): AuthRepository

    companion object {
        @Provides
        fun providesFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    }
}
