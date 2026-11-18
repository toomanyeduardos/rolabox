package com.eduardoflores.rolabox.device.podcasts.impl.di

import com.eduardoflores.rolabox.device.podcasts.api.EpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultEpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultPodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultPodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.impl.EpisodeDownloads
import com.eduardoflores.rolabox.device.podcasts.impl.NoEpisodeDownloads
import com.eduardoflores.rolabox.device.podcasts.impl.artwork.DefaultPodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.impl.artwork.PodcastArtworkScheduler
import com.eduardoflores.rolabox.device.podcasts.impl.artwork.WorkManagerPodcastArtworkScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsModule {
    @Binds
    internal abstract fun bindsPodcastsRepository(repository: DefaultPodcastsRepository): PodcastsRepository

    @Binds
    internal abstract fun bindsEpisodeProgressRepository(
        repository: DefaultEpisodeProgressRepository,
    ): EpisodeProgressRepository

    @Binds
    internal abstract fun bindsPodcastDirectory(directory: DefaultPodcastDirectory): PodcastDirectory

    @Binds
    internal abstract fun bindsPodcastArtworkRepository(
        repository: DefaultPodcastArtworkRepository,
    ): PodcastArtworkRepository

    @Binds
    internal abstract fun bindsPodcastArtworkScheduler(
        scheduler: WorkManagerPodcastArtworkScheduler,
    ): PodcastArtworkScheduler

    @Binds
    internal abstract fun bindsEpisodeDownloads(downloads: NoEpisodeDownloads): EpisodeDownloads
}
