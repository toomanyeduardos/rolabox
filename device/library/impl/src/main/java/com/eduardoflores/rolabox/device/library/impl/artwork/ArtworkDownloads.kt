package com.eduardoflores.rolabox.device.library.impl.artwork

import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.library.impl.LibraryScanner
import com.eduardoflores.rolabox.device.library.impl.ScanPhase
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Where the download runs: WorkManager's work in the app, a fake in tests. */
internal interface ArtworkDownloadScheduler {
    /** Starts the download, unless it is already running, with a network constraint. */
    fun enqueue()

    /** Stops the download, and drops what was enqueued. */
    fun cancel()
}

/**
 * Starts the download when a scan ends (ADR-027, When): the pass looks at the albums with no record, and the work is
 * enqueued when one is due. While downloading isn't allowed, nothing is looked at, and the work is cancelled.
 */
@Singleton
internal class ArtworkDownloads @Inject constructor(
    private val scanner: LibraryScanner,
    private val permit: DownloadPermit,
    private val pass: MissingArtworkPass,
    private val scheduler: ArtworkDownloadScheduler,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val started = AtomicBoolean(false)

    /** Follows the scans and the permit for as long as the process lives. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(ioDispatcher) {
            combine(scanner.phase, permit.observe()) { phase, allowed -> (phase == ScanPhase.Finished) to allowed }
                .distinctUntilChanged()
                .collectLatest { (scanned, allowed) ->
                    when {
                        !allowed -> scheduler.cancel()

                        // A pass that fails loses nothing: the next scan's pass looks again (ADR-007, rule 7).
                        scanned -> pass.run().onRight { due -> if (due) scheduler.enqueue() }
                    }
                }
        }
    }
}
