package com.eduardoflores.rolabox.device.playback.impl.di

import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.impl.MediaPlayback
import com.eduardoflores.rolabox.device.playback.impl.PlayerConnection
import com.eduardoflores.rolabox.device.playback.impl.SessionConnection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    internal abstract fun bindsPlaybackState(playback: MediaPlayback): PlaybackState

    @Binds
    internal abstract fun bindsPlaybackController(playback: MediaPlayback): PlaybackController

    @Binds
    internal abstract fun bindsPlayerConnection(connection: SessionConnection): PlayerConnection
}
