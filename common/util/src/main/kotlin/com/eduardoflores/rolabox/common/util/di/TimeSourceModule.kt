package com.eduardoflores.rolabox.common.util.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.TimeSource

// For measuring how much time has passed, where the Clock is for what time it is. Injected so tests can
// move it, together with the dispatchers that their delays run on.
@Module
@InstallIn(SingletonComponent::class)
object TimeSourceModule {
    @Provides
    fun providesTimeSource(): TimeSource.WithComparableMarks = TimeSource.Monotonic
}
