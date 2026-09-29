package com.eduardoflores.rolabox.core.sync.impl

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager creates workers itself, so this one gets its dependency from a Hilt entry point
 * instead of its constructor (ADR-011).
 */
internal class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val syncRunner = EntryPointAccessors.fromApplication<SyncWorkerEntryPoint>(applicationContext).syncRunner()
        return when (syncRunner.sync()) {
            SyncOutcome.Done -> Result.success()
            SyncOutcome.Retry -> Result.retry()
            SyncOutcome.Failed -> Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SyncWorkerEntryPoint {
    fun syncRunner(): SyncRunner
}
