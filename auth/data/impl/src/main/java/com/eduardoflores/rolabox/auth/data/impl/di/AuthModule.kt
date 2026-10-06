package com.eduardoflores.rolabox.auth.data.impl.di

import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.impl.FirebaseAuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
