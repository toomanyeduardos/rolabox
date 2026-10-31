package com.eduardoflores.rolabox.device.library.local.impl.di

import android.content.Context
import androidx.room.Room
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryIndexDao
import com.eduardoflores.rolabox.device.library.local.impl.index.RoomLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.MediaStoreAudioFilesLocalDataSource
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

    internal companion object {
        @Provides
        @Singleton
        fun providesLibraryDatabase(@ApplicationContext context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, LibraryDatabase.NAME).build()

        @Provides
        fun providesLibraryIndexDao(database: LibraryDatabase): LibraryIndexDao = database.libraryIndexDao()
    }
}
