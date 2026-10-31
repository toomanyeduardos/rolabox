package com.eduardoflores.rolabox

import android.app.Application
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import com.eduardoflores.rolabox.common.util.ProcessStartTask
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RolaboxApplication : Application() {
    @Inject
    lateinit var syncRepository: SyncRepository

    @Inject
    lateinit var processStartTasks: Set<@JvmSuppressWildcards ProcessStartTask>

    override fun onCreate() {
        super.onCreate()
        syncRepository.start()
        processStartTasks.forEach(ProcessStartTask::onProcessStart)
    }
}
