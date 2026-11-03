package com.eduardoflores.rolabox.device.library.local.impl.di

import android.content.Context
import androidx.room.Room
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.TagsLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.artwork.RoomAlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.index.AlbumArtworkDao
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryIndexDao
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_1_2
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_2_3
import com.eduardoflores.rolabox.device.library.local.impl.index.RoomLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.MediaStoreArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.MediaStoreAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.tags.ContentResolverTagsLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryLocalModule {
    @Binds
    internal abstract fun bindsLibraryIndexLocalDataSource(
        dataSource: RoomLibraryIndexLocalDataSource,
    ): LibraryIndexLocalDataSource

    @Binds
    internal abstract fun bindsAudioFilesLocalDataSource(
        dataSource: MediaStoreAudioFilesLocalDataSource,
    ): AudioFilesLocalDataSource

    @Binds
    internal abstract fun bindsArtworkLocalDataSource(
        dataSource: MediaStoreArtworkLocalDataSource,
    ): ArtworkLocalDataSource

    @Binds
    internal abstract fun bindsAlbumArtworkLocalDataSource(
        dataSource: RoomAlbumArtworkLocalDataSource,
    ): AlbumArtworkLocalDataSource

    @Binds
    internal abstract fun bindsTagsLocalDataSource(dataSource: ContentResolverTagsLocalDataSource): TagsLocalDataSource

    internal companion object {
        @Provides
        @Singleton
        fun providesLibraryDatabase(@ApplicationContext context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, LibraryDatabase.NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        @Provides
        fun providesLibraryIndexDao(database: LibraryDatabase): LibraryIndexDao = database.libraryIndexDao()

        @Provides
        fun providesAlbumArtworkDao(database: LibraryDatabase): AlbumArtworkDao = database.albumArtworkDao()
    }
}
