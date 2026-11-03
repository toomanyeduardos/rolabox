package com.eduardoflores.rolabox.device.library.impl.artwork

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The album whose artwork is shown, which the download asks for next (ADR-027, The work). Only the last one shown is
 * wanted, and only in memory: a new process starts in the library's order.
 */
@Singleton
internal class WantedAlbum @Inject constructor() {
    private val key = AtomicReference<String?>(null)

    fun want(albumKey: String) = key.set(albumKey)

    fun get(): String? = key.get()
}
