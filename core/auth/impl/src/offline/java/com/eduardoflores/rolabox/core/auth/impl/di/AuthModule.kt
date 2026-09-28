package com.eduardoflores.rolabox.core.auth.impl.di

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.impl.SignedOutAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    internal abstract fun bindsAuthRepository(repository: SignedOutAuthRepository): AuthRepository
}
