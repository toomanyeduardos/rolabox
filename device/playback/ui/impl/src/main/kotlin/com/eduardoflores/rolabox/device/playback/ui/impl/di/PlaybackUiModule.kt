package com.eduardoflores.rolabox.device.playback.ui.impl.di

import com.eduardoflores.rolabox.device.playback.ui.api.PlaybackEntries
import com.eduardoflores.rolabox.device.playback.ui.impl.DefaultPlaybackEntries
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the part's `:api`: its entry contract, which the device injects. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackUiModule {
    @Binds
    internal abstract fun bindsPlaybackEntries(entries: DefaultPlaybackEntries): PlaybackEntries
}
