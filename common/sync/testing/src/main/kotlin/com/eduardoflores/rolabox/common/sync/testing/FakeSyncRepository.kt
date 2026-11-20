package com.eduardoflores.rolabox.common.sync.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSyncRepository @Inject constructor() : SyncRepository {
    var started = false
        private set

    var syncRequests = 0
        private set

    /** When set, [deleteRemoteData] fails with this error, and sync stays paused as with the real one. */
    var deleteRemoteError: RemoteError? = null

    /** The user whose sync is paused by [deleteRemoteData], until [resume]. */
    var pausedFor: String? = null
        private set

    /** The users whose remote data [deleteRemoteData] deleted, in order. */
    val deletedRemoteFor = mutableListOf<String>()

    /** Every call of the deletion functions, in order, for tests of the order of a deletion. */
    val calls = mutableListOf<String>()

    override fun start() {
        started = true
    }

    override fun requestSync() {
        syncRequests++
    }

    override suspend fun deleteRemoteData(userId: String): Either<RemoteError, Unit> {
        calls += "deleteRemoteData($userId)"
        pausedFor = userId
        return deleteRemoteError?.left() ?: deletedRemoteFor.add(userId).let { Unit.right() }
    }

    override fun resume() {
        calls += "resume"
        pausedFor = null
    }
}
