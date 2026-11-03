package com.eduardoflores.rolabox.device.library.impl.artwork

/** Keeps what was asked of WorkManager, in [calls], in order. */
internal class FakeArtworkDownloadScheduler : ArtworkDownloadScheduler {
    val calls = mutableListOf<String>()

    override fun enqueue() {
        calls += "enqueue"
    }

    override fun cancel() {
        calls += "cancel"
    }
}
