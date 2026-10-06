package com.eduardoflores.rolabox.common.util.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

// Injected so tests can fix the time, the same way dispatchers are injected.
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun providesClock(): Clock = Clock.systemUTC()
}
