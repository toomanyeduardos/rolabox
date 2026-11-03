package com.eduardoflores.rolabox.device.library.impl.di

import com.eduardoflores.rolabox.common.util.ProcessStartTask
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.impl.IndexLibraryRepository
import com.eduardoflores.rolabox.device.library.impl.artwork.ArtworkDownloadScheduler
import com.eduardoflores.rolabox.device.library.impl.artwork.ArtworkDownloads
import com.eduardoflores.rolabox.device.library.impl.artwork.CachedArtworkRepository
import com.eduardoflores.rolabox.device.library.impl.artwork.WorkManagerArtworkDownloadScheduler
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

    @Binds
    internal abstract fun bindsArtworkRepository(repository: CachedArtworkRepository): ArtworkRepository

    @Binds
    internal abstract fun bindsArtworkDownloadScheduler(
        scheduler: WorkManagerArtworkDownloadScheduler,
    ): ArtworkDownloadScheduler

    internal companion object {
        /** Each start of the process scans, if the permission is granted (ADR-024). */
        @Provides
        @IntoSet
        fun providesCheckPermissionOnStart(repository: LibraryRepository) =
            ProcessStartTask(repository::checkPermission)

        /** Each start of the process follows the scans, to download the covers missing from the phone (ADR-027). */
        @Provides
        @IntoSet
        fun providesFollowScansForArtwork(downloads: ArtworkDownloads) = ProcessStartTask(downloads::start)
    }
}
