package com.eduardoflores.rolabox.device.podcasts.network.impl.di

import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.impl.DeviceCountry
import com.eduardoflores.rolabox.device.podcasts.network.impl.DirectorySearchUrl
import com.eduardoflores.rolabox.device.podcasts.network.impl.ITunesDirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.impl.OkHttpImageNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.impl.PodcastsClient
import com.eduardoflores.rolabox.device.podcasts.network.impl.RssFeedNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsNetworkModule {
    @Binds
    internal abstract fun bindsDirectoryNetworkDataSource(
        dataSource: ITunesDirectoryNetworkDataSource,
    ): DirectoryNetworkDataSource

    @Binds
    internal abstract fun bindsFeedNetworkDataSource(dataSource: RssFeedNetworkDataSource): FeedNetworkDataSource

    @Binds
    internal abstract fun bindsImageNetworkDataSource(dataSource: OkHttpImageNetworkDataSource): ImageNetworkDataSource

    internal companion object {
        private val CONNECT_TIMEOUT = 15.seconds

        // Between two reads of a body, so that a feed of thousands of items may take as long as it streams.
        private val READ_TIMEOUT = 30.seconds

        @Provides
        @DirectorySearchUrl
        fun providesDirectorySearchUrl(): HttpUrl = "https://itunes.apple.com/search".toHttpUrl()

        @Provides
        fun providesDeviceCountry(): DeviceCountry = DeviceCountry.FromLocale

        /**
         * The podcasts' client, for the directory, the feeds and their images. It sends nothing of its own (ADR-031,
         * rule 1).
         */
        @Provides
        @Singleton
        @PodcastsClient
        fun providesPodcastsClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT.toJavaDuration())
            .readTimeout(READ_TIMEOUT.toJavaDuration())
            .build()
    }
}
