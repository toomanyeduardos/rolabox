package com.eduardoflores.rolabox.core.auth.di

import com.eduardoflores.rolabox.core.auth.SignedOutAuthRepository
import com.eduardoflores.rolabox.core.domain.repository.AuthRepository
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
