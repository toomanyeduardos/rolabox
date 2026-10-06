package com.eduardoflores.rolabox.device.library.impl.di

import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.impl.InMemoryLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    internal abstract fun bindsLibraryRepository(repository: InMemoryLibraryRepository): LibraryRepository
}
