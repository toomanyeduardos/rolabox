package com.eduardoflores.rolabox.device.library.impl.di

import com.eduardoflores.rolabox.common.util.ProcessStartTask
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.impl.IndexLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    internal abstract fun bindsLibraryRepository(repository: IndexLibraryRepository): LibraryRepository

    internal companion object {
        /** Each start of the process scans, if the permission is granted (ADR-024). */
        @Provides
        @IntoSet
        fun providesCheckPermissionOnStart(repository: LibraryRepository) =
            ProcessStartTask(repository::checkPermission)
    }
}
