package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.storage.api.StorageError

/** Why one sync failed. Internal: sync runs in the background, and no caller sees its result. */
internal sealed interface SyncError {
    data class Local(val cause: StorageError) : SyncError

    data class Remote(val cause: RemoteError) : SyncError
}

internal sealed interface RemoteError {
    /** Offline, timed out, or contended. Trying again later can succeed. */
    data object Unavailable : RemoteError

    /** The security rules turned the request down. That's a bug, and trying again won't help. */
    data object Rejected : RemoteError
}

/** Whether WorkManager should try the sync again later, instead of giving up until the next trigger. */
internal val SyncError.isRetryable: Boolean
    get() = when (this) {
        is SyncError.Local -> cause == StorageError.Unavailable
        is SyncError.Remote -> cause == RemoteError.Unavailable
    }
