package com.eduardoflores.rolabox.device.playback.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.playback.local.api.PlayOrder
import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint

/**
 * The point and the order in memory, which a test sets to what an earlier process stored, and reads to see what this
 * one did. [orderWrites] counts the writes of the order, which are only made when it changes (ADR-028).
 */
class FakePlaybackLocalDataSource(var point: ResumePoint? = null, var order: PlayOrder? = null) :
    PlaybackLocalDataSource {
    var orderWrites = 0
        private set

    override suspend fun resumePoint(): Either<StorageError, ResumePoint?> = point.right()

    override suspend fun store(point: ResumePoint): Either<StorageError, Unit> {
        this.point = point
        return Unit.right()
    }

    override suspend fun playOrder(): Either<StorageError, PlayOrder?> = order.right()

    override suspend fun storeOrder(order: PlayOrder?): Either<StorageError, Unit> {
        this.order = order
        orderWrites++
        return Unit.right()
    }

    override suspend fun clear(): Either<StorageError, Unit> {
        point = null
        order = null
        return Unit.right()
    }
}
