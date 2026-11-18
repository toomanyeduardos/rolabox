package com.eduardoflores.rolabox.device.playback.api

import kotlinx.coroutines.flow.Flow

/**
 * The download of each item that has one, which only playing an episode starts (ADR-031, Downloads). An item missing
 * from the map has no download: it was never played, or its download was removed.
 */
interface ItemDownloads {
    /** Every download, by item, and again each time one changes, its fraction included while it runs. */
    fun observe(): Flow<Map<PlaybackItemId, ItemDownload>>
}

/** Where an item's download is (ADR-031, Downloads). Every [fraction] is of the bytes, from 0 to 1. */
sealed interface ItemDownload {
    /** Added and not running: queued, or waiting for a network it may use, with what it has so far. */
    data class Waiting(val fraction: Float) : ItemDownload

    /** Running. */
    data class Downloading(val fraction: Float) : ItemDownload

    /** Every byte is on the phone, so the item plays with no network. */
    data object Done : ItemDownload

    /** It stopped for good after Media3's retries. Playing the item adds it again. */
    data object Failed : ItemDownload
}
