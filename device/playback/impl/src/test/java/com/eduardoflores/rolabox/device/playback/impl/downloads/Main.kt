package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import org.robolectric.shadows.ShadowLooper

/**
 * Drives Robolectric's main looper while Media3's download threads and the loopback server run on real threads: each
 * step runs what is due on the main thread, moves the looper's clock on by [STEP_MS], whose delays Media3's retries and
 * the downloads' Tick are, and gives the real threads a moment.
 */
internal object Main {
    /** The main thread's dispatcher, on Robolectric's main looper. */
    val dispatcher: CoroutineDispatcher get() = Handler(Looper.getMainLooper()).asCoroutineDispatcher()

    /** Real threads, for the disk. */
    val io: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    /** Runs [block] on the main thread, and steps until it has returned. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> run(block: suspend () -> T): T {
        val running = CoroutineScope(dispatcher).async(start = CoroutineStart.UNDISPATCHED) { block() }
        until("the call returns") { running.isCompleted }
        return running.getCompleted()
    }

    /** Steps until [done], or fails once [MAX_STEPS] have gone by. */
    fun until(what: String, done: () -> Boolean) {
        repeat(MAX_STEPS) {
            if (done()) return
            step()
        }
        error("Timed out waiting until $what")
    }

    fun steps(count: Int) = repeat(count) { step() }

    private fun step() {
        ShadowLooper.idleMainLooper(STEP_MS, TimeUnit.MILLISECONDS)
        Thread.sleep(REAL_PAUSE_MS)
    }

    private const val STEP_MS = 50L
    private const val REAL_PAUSE_MS = 5L
    private const val MAX_STEPS = 4_000
}
