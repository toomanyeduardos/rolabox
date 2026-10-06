package com.eduardoflores.rolabox

import android.app.Application
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RolaboxApplication : Application() {
    @Inject
    lateinit var syncRepository: SyncRepository

    override fun onCreate() {
        super.onCreate()
        syncRepository.start()
    }
}
