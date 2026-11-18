package com.eduardoflores.rolabox.device.podcasts.local.impl.di

import android.content.Context
import androidx.room.Room
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.impl.FilePodcastArtworkLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.impl.PodcastsDao
import com.eduardoflores.rolabox.device.podcasts.local.impl.PodcastsDatabase
import com.eduardoflores.rolabox.device.podcasts.local.impl.ProgressDao
import com.eduardoflores.rolabox.device.podcasts.local.impl.RoomEpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.impl.RoomPodcastsLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsLocalModule {
    @Binds
    internal abstract fun bindsPodcastsLocalDataSource(dataSource: RoomPodcastsLocalDataSource): PodcastsLocalDataSource

    @Binds
    internal abstract fun bindsPodcastArtworkLocalDataSource(
        dataSource: FilePodcastArtworkLocalDataSource,
    ): PodcastArtworkLocalDataSource

    @Binds
    internal abstract fun bindsEpisodeProgressLocalDataSource(
        dataSource: RoomEpisodeProgressLocalDataSource,
    ): EpisodeProgressLocalDataSource

    internal companion object {
        @Provides
        @Singleton
        fun providesPodcastsDatabase(@ApplicationContext context: Context): PodcastsDatabase =
            Room.databaseBuilder(context, PodcastsDatabase::class.java, PodcastsDatabase.NAME)
                .apply { PodcastsDatabase.MIGRATIONS.forEach { addMigrations(it) } }
                .build()

        @Provides
        fun providesPodcastsDao(database: PodcastsDatabase): PodcastsDao = database.podcastsDao()

        @Provides
        fun providesProgressDao(database: PodcastsDatabase): ProgressDao = database.progressDao()
    }
}
