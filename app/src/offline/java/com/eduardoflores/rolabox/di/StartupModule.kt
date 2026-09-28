package com.eduardoflores.rolabox.di

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.domain.ResolveStartDestination
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Both flavors declare this module under the same name (ADR-008); only whether accounts exist differs.
@Module
@InstallIn(SingletonComponent::class)
object StartupModule {
    @Provides
    fun providesResolveStartDestination(
        authRepository: AuthRepository,
        userDataRepository: UserDataRepository,
    ): ResolveStartDestination = ResolveStartDestination(
        authRepository = authRepository,
        userDataRepository = userDataRepository,
        accountsAvailable = false,
    )
}
