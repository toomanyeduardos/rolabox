package com.eduardoflores.rolabox.device.podcasts.impl.di

import com.eduardoflores.rolabox.common.util.ProcessStartTask
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultEpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultPodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.impl.DefaultPodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.impl.EpisodeDownloads
import com.eduardoflores.rolabox.device.podcasts.impl.PlaybackEpisodeDownloads
import com.eduardoflores.rolabox.device.podcasts.impl.artwork.DefaultPodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.impl.artwork.PodcastArtworkScheduler
import com.eduardoflores.rolabox.device.podcasts.impl.artwork.WorkManagerPodcastArtworkScheduler
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckLog
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckOnProcessStart
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckScheduler
import com.eduardoflores.rolabox.device.podcasts.impl.follow.LogcatFollowCheckLog
import com.eduardoflores.rolabox.device.podcasts.impl.follow.WorkManagerFollowCheckScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

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
    internal abstract fun bindsEpisodeDownloads(downloads: PlaybackEpisodeDownloads): EpisodeDownloads

    @Binds
    internal abstract fun bindsFollowCheckScheduler(scheduler: WorkManagerFollowCheckScheduler): FollowCheckScheduler

    /** Each start of the process requests the follow check (ADR-031, Follow checks; ADR-025). */
    @Binds
    @IntoSet
    internal abstract fun bindsFollowCheckOnProcessStart(task: FollowCheckOnProcessStart): ProcessStartTask
}

/**
 * The follow check's log, apart from [PodcastsModule]: the check's worker reaches it through an entry point that is
 * in every graph, so a test that replaces [PodcastsModule] still has to provide it. That test drops the process-start
 * task and the scheduler with [PodcastsModule], so the check never runs there (ADR-025).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FollowCheckModule {
    @Binds
    internal abstract fun bindsFollowCheckLog(log: LogcatFollowCheckLog): FollowCheckLog
}
