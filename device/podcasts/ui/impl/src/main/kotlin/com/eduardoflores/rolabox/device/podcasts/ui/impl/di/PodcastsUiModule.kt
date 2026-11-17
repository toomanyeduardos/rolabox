package com.eduardoflores.rolabox.device.podcasts.ui.impl.di

import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastsEntries
import com.eduardoflores.rolabox.device.podcasts.ui.impl.DefaultPodcastsEntries
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the part's `:api`: its entry contract, which the device injects. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsUiModule {
    @Binds
    internal abstract fun bindsPodcastsEntries(entries: DefaultPodcastsEntries): PodcastsEntries
}
