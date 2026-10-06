package com.eduardoflores.rolabox.device.music.impl.di

import com.eduardoflores.rolabox.device.music.api.MusicEntries
import com.eduardoflores.rolabox.device.music.impl.DefaultMusicEntries
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
