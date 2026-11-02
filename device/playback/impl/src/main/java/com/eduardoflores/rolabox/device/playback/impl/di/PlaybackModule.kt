package com.eduardoflores.rolabox.device.playback.impl.di

import com.eduardoflores.rolabox.common.util.ProcessStartTask
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.impl.MediaPlayback
import com.eduardoflores.rolabox.device.playback.impl.PlayerConnection
import com.eduardoflores.rolabox.device.playback.impl.RestoreOnProcessStart
import com.eduardoflores.rolabox.device.playback.impl.SessionConnection
import com.eduardoflores.rolabox.device.playback.impl.SessionResumption
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
}

/** Apart from [PlaybackModule], so the service still has its graph in tests that replace that module. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackServiceModule {
    @BindsOptionalOf
    internal abstract fun optionalSessionResumption(): SessionResumption
}
