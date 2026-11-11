package com.eduardoflores.rolabox.common.sync.api

import com.eduardoflores.rolabox.common.storage.api.StorageError

/** Why one sync failed. Sync runs in the background, and only the sync runner sees it. */
sealed interface SyncError {
    data class Local(val cause: StorageError) : SyncError

    data class Remote(val cause: RemoteError) : SyncError
}

/** Why a remote data source failed. Each `:network:impl` turns its SDK's exceptions into these. */
sealed interface RemoteError {
    /** Offline, timed out, or contended. Trying again later can succeed. */
    data object Unavailable : RemoteError

    /**
     * The backend turned the request down: the security rules refused it, it was invalid, or the
     * backend isn't set up (no database). That's a bug or a setup mistake, and trying again won't help.
     */
    data object Rejected : RemoteError
}
