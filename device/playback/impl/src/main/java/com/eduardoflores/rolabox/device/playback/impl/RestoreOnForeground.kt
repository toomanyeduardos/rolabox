package com.eduardoflores.rolabox.device.playback.impl

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.ProcessStartTask
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Loads what was stored each time the app comes to the front, and stores where it is each time it goes to the
 * background (ADR-023). Never at the start of the process alone: a process the system starts in the background, for a
 * bind of the session, a job or a media button, leaves the player empty, so no media notification shows with the app
 * never opened (ADR-045, rule 1). A play with nothing loaded gets the stored point through the session's resumption.
 */
internal class RestoreOnForeground(
    private val playback: MediaPlayback,
    private val scope: CoroutineScope,
    private val lifecycle: () -> Lifecycle,
) : ProcessStartTask {
    @Inject
    constructor(playback: MediaPlayback, @ApplicationScope scope: CoroutineScope) :
        this(playback, scope, { ProcessLifecycleOwner.get().lifecycle })

    override fun onProcessStart() {
        // The process lifecycle must be observed from the main thread, which a task runs on.
        lifecycle().addObserver(
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
