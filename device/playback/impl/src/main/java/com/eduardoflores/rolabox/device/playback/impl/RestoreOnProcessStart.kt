package com.eduardoflores.rolabox.device.playback.impl

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.ProcessStartTask
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Loads what was stored at each start of the process, and again each time the app comes back: dismissing it while
 * paused releases the session, and the process may outlive that. Going to the background stores where it is (ADR-023).
 */
internal class RestoreOnProcessStart @Inject constructor(
    private val playback: MediaPlayback,
    @ApplicationScope private val scope: CoroutineScope,
) : ProcessStartTask {
    override fun onProcessStart() {
        scope.launch { playback.restore() }
        // The process lifecycle must be observed from the main thread, which a task runs on.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { playback.restore() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    scope.launch { playback.storeNow() }
                }
            },
        )
    }
}
