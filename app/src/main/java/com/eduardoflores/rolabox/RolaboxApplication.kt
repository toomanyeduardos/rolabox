package com.eduardoflores.rolabox

import android.app.Application
import com.eduardoflores.rolabox.core.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RolaboxApplication : Application() {
    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        syncManager.requestSync()
    }
}
