package com.eduardoflores.rolabox.device.playback.local.impl.di

import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.impl.DefaultPlaybackLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackLocalModule {
    @Binds
    internal abstract fun bindsPlaybackLocalDataSource(
        dataSource: DefaultPlaybackLocalDataSource,
    ): PlaybackLocalDataSource
}
