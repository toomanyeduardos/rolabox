package com.eduardoflores.rolabox.device.library.impl.di

import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.impl.StandInLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// For debug builds only: this file is in src/debug. The debug build plays the files on the phone, until
// library/03 binds the index in both builds. The release build's module of the same name, in src/release,
// binds the sample library.
@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    internal abstract fun bindsLibraryRepository(repository: StandInLibraryRepository): LibraryRepository
}
