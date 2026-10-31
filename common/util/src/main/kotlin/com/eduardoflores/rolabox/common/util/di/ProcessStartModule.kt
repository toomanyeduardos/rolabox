package com.eduardoflores.rolabox.common.util.di

import com.eduardoflores.rolabox.common.util.ProcessStartTask
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the set, so it exists when no part adds to it, as in tests that replace those parts. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessStartModule {
    @Multibinds
    abstract fun processStartTasks(): Set<ProcessStartTask>
}
