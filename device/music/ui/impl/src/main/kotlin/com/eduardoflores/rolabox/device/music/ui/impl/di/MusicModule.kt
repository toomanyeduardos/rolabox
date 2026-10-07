package com.eduardoflores.rolabox.device.music.ui.impl.di

import com.eduardoflores.rolabox.device.music.ui.api.MusicEntries
import com.eduardoflores.rolabox.device.music.ui.impl.DefaultMusicEntries
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the part's `:api`: its entry contract, which the device injects. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MusicModule {
    @Binds
    internal abstract fun bindsMusicEntries(entries: DefaultMusicEntries): MusicEntries
}
