package com.eduardoflores.rolabox.common.crashreporting.impl.di

import com.eduardoflores.rolabox.common.crashreporting.impl.LogcatNonFatalReport
import com.eduardoflores.rolabox.common.util.NonFatalReport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug builds don't start Crashlytics (ADR-030 rule 7). The release build's module binds it instead. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CrashReportingModule {
    @Binds
    internal abstract fun bindsNonFatalReport(report: LogcatNonFatalReport): NonFatalReport
}
