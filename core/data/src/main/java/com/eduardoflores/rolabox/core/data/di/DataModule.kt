package com.eduardoflores.rolabox.core.data.di

import com.eduardoflores.rolabox.core.data.repository.DefaultUserDataRepository
import com.eduardoflores.rolabox.core.data.repository.UserDataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    internal abstract fun bindsUserDataRepository(repository: DefaultUserDataRepository): UserDataRepository
}
