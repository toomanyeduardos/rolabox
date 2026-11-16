package com.eduardoflores.rolabox.common.crashreporting.impl.di

import com.eduardoflores.rolabox.common.crashreporting.impl.CrashlyticsNonFatalReport
import com.eduardoflores.rolabox.common.util.NonFatalReport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashReportingModule {
    @Binds
    internal abstract fun bindsNonFatalReport(report: CrashlyticsNonFatalReport): NonFatalReport
}
