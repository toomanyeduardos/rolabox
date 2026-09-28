package com.eduardoflores.rolabox.core.sync.api

/**
 * When a synced value was last changed, in milliseconds since the epoch, read from the clock of the
 * device that changed it. Milliseconds, because Firestore keeps timestamps to the microsecond, and a
 * finer local value would never compare equal after a round trip.
 */
@JvmInline
value class SyncTimestamp(val epochMillis: Long) : Comparable<SyncTimestamp> {
    override fun compareTo(other: SyncTimestamp): Int = epochMillis.compareTo(other.epochMillis)

    companion object {
        /** For values stored before they carried a timestamp: older than any real change. */
        val Epoch = SyncTimestamp(0)
    }
}

/** A synced value and when it was last changed. */
data class SyncedValue<out T>(val value: T, val updatedAt: SyncTimestamp)
