package com.eduardoflores.rolabox.device.playback.impl.di

import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.impl.InMemoryPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    internal abstract fun bindsPlaybackState(player: InMemoryPlayer): PlaybackState

    @Binds
    internal abstract fun bindsPlaybackController(player: InMemoryPlayer): PlaybackController
}
