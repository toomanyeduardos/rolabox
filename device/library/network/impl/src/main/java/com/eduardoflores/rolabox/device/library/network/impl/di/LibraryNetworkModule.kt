package com.eduardoflores.rolabox.device.library.network.impl.di

import android.content.Context
import com.eduardoflores.rolabox.device.library.network.api.CoverNetworkDataSource
import com.eduardoflores.rolabox.device.library.network.impl.CoverClient
import com.eduardoflores.rolabox.device.library.network.impl.MusicBrainzCoverNetworkDataSource
import com.eduardoflores.rolabox.device.library.network.impl.RequestSpacing
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration
import okhttp3.OkHttpClient

/** Where the project can be reached, which MusicBrainz asks every client's `User-Agent` to say. */
private const val CONTACT = "https://github.com/toomanyeduardos/rolabox"

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryNetworkModule {
    @Binds
    internal abstract fun bindsCoverNetworkDataSource(
        dataSource: MusicBrainzCoverNetworkDataSource,
    ): CoverNetworkDataSource

    internal companion object {
        /** One request a second, to either host, for the whole app (ADR-027, rule 2). */
        @Provides
        @Singleton
        fun providesRequestSpacing(): RequestSpacing = RequestSpacing(1.seconds, TimeSource.Monotonic)

        @Provides
        @Singleton
        @CoverClient
        fun providesCoverClient(@ApplicationContext context: Context): OkHttpClient {
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            val userAgent = "Rolabox/$version ( $CONTACT )"
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                }
                .callTimeout(CALL_TIMEOUT.toJavaDuration())
                .build()
        }

        private val CALL_TIMEOUT = 30.seconds
    }
}
