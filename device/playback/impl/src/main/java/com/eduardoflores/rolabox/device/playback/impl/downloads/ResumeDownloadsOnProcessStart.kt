package com.eduardoflores.rolabox.device.playback.impl.downloads

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.ProcessStartTask
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Makes the download manager at each start of the process, which resumes a download a kill stopped, and starts the
 * download service for it each time the app comes to the front, when the system lets a foreground service start
 * (ADR-031, Downloads; ADR-025).
 */
internal class ResumeDownloadsOnProcessStart @Inject constructor(
    private val downloads: MediaDownloads,
    @ApplicationScope private val scope: CoroutineScope,
) : ProcessStartTask {
    override fun onProcessStart() {
        // The process lifecycle must be observed from the main thread, which a task runs on.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { downloads.resumeUnfinished() }
                }
            },
        )
    }
}
