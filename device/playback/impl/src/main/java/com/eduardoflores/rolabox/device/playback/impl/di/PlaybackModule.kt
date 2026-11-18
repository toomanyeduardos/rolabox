package com.eduardoflores.rolabox.device.playback.impl.di

import com.eduardoflores.rolabox.common.util.ProcessStartTask
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.playback.api.EpisodeNetwork
import com.eduardoflores.rolabox.device.playback.api.ItemDownloads
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.impl.MediaPlayback
import com.eduardoflores.rolabox.device.playback.impl.PlayerConnection
import com.eduardoflores.rolabox.device.playback.impl.RestoreOnProcessStart
import com.eduardoflores.rolabox.device.playback.impl.SessionConnection
import com.eduardoflores.rolabox.device.playback.impl.SessionResumption
import com.eduardoflores.rolabox.device.playback.impl.downloads.ConnectivityPhoneNetwork
import com.eduardoflores.rolabox.device.playback.impl.downloads.DownloadServiceStarter
import com.eduardoflores.rolabox.device.playback.impl.downloads.EpisodeDownloader
import com.eduardoflores.rolabox.device.playback.impl.downloads.EpisodeNetworkPolicy
import com.eduardoflores.rolabox.device.playback.impl.downloads.MediaDownloads
import com.eduardoflores.rolabox.device.playback.impl.downloads.PhoneNetwork
import com.eduardoflores.rolabox.device.playback.impl.downloads.ResumeDownloadsOnProcessStart
import com.eduardoflores.rolabox.device.playback.impl.downloads.StartEpisodeDownloadService
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    internal abstract fun bindsPlaybackState(playback: MediaPlayback): PlaybackState

    @Binds
    internal abstract fun bindsPlaybackController(playback: MediaPlayback): PlaybackController

    @Binds
    internal abstract fun bindsPlayerConnection(connection: SessionConnection): PlayerConnection

    @Binds
    internal abstract fun bindsSessionResumption(playback: MediaPlayback): SessionResumption

    /** Each start of the process loads what was stored (ADR-023, ADR-025). */
    @Binds
    @IntoSet
    internal abstract fun bindsRestoreOnProcessStart(task: RestoreOnProcessStart): ProcessStartTask

    /** Episodes download into the cache the player reads (ADR-031, Downloads). */
    @Binds
    internal abstract fun bindsItemDownloads(downloads: MediaDownloads): ItemDownloads

    @Binds
    internal abstract fun bindsEpisodeDownloader(downloads: MediaDownloads): EpisodeDownloader

    @Binds
    internal abstract fun bindsEpisodeNetwork(policy: EpisodeNetworkPolicy): EpisodeNetwork

    /** Each start of the process makes the download manager, which resumes what a kill stopped (ADR-025). */
    @Binds
    @IntoSet
    internal abstract fun bindsResumeDownloadsOnProcessStart(task: ResumeDownloadsOnProcessStart): ProcessStartTask
}

/** Apart from [PlaybackModule], so the service still has its graph in tests that replace that module. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackServiceModule {
    @BindsOptionalOf
    internal abstract fun optionalSessionResumption(): SessionResumption

    @BindsOptionalOf
    internal abstract fun optionalArtworkRepository(): ArtworkRepository

    @BindsOptionalOf
    internal abstract fun optionalPodcastArtworkRepository(): PodcastArtworkRepository

    /** The network the service's player follows for episodes (ADR-031, rule 9). */
    @Binds
    internal abstract fun bindsPhoneNetwork(network: ConnectivityPhoneNetwork): PhoneNetwork

    /** How the download service starts, which its own downloads need. */
    @Binds
    internal abstract fun bindsDownloadServiceStarter(starter: StartEpisodeDownloadService): DownloadServiceStarter
}
