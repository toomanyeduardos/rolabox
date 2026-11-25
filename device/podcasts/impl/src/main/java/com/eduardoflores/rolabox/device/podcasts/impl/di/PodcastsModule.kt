package com.eduardoflores.rolabox.device.podcasts.impl.di

import com.eduardoflores.rolabox.common.sync.api.Syncer
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
import com.eduardoflores.rolabox.device.podcasts.impl.follow.EpisodeOrdersSyncer
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckLog
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckOnProcessStart
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowCheckScheduler
import com.eduardoflores.rolabox.device.podcasts.impl.follow.FollowsSyncer
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

    /** Each start of the process requests the follow check (ADR-031, Follow checks; ADR-025). */
    @Binds
    @IntoSet
    internal abstract fun bindsFollowCheckOnProcessStart(task: FollowCheckOnProcessStart): ProcessStartTask
}

/**
 * The follow check's log, apart from [PodcastsModule]: the check's worker reaches it through an entry point that is
 * in every graph, so a test that replaces [PodcastsModule] still has to provide it. That test drops the process-start
 * task with [PodcastsModule], so the check never runs at start there (ADR-025).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FollowCheckModule {
    @Binds
    internal abstract fun bindsFollowCheckLog(log: LogcatFollowCheckLog): FollowCheckLog
}

/**
 * Adds the follows, and the choices of episode order (ADR-043), to what sync runs (ADR-022, rule 5), with the
 * scheduler the follows' `Syncer` requests feed reads from. It's apart from [PodcastsModule] so that a test that
 * replaces the repositories keeps the `Syncer`s in the graph, as the preferences' is kept (ADR-022, rule 8).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FollowsSyncerModule {
    @Binds
    @IntoSet
    internal abstract fun bindsFollowsSyncer(syncer: FollowsSyncer): Syncer

    /** The choices of episode order sync apart from the follows (ADR-043, Syncing the choice). */
    @Binds
    @IntoSet
    internal abstract fun bindsEpisodeOrdersSyncer(syncer: EpisodeOrdersSyncer): Syncer

    @Binds
    internal abstract fun bindsFollowCheckScheduler(scheduler: WorkManagerFollowCheckScheduler): FollowCheckScheduler
}
