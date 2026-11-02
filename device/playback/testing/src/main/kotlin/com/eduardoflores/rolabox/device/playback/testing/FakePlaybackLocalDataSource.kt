package com.eduardoflores.rolabox.device.playback.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.playback.local.api.PlaybackLocalDataSource
import com.eduardoflores.rolabox.device.playback.local.api.ResumePoint

/** The point in memory, which a test sets to what an earlier process stored, and reads to see what this one did. */
class FakePlaybackLocalDataSource(var point: ResumePoint? = null) : PlaybackLocalDataSource {
    override suspend fun resumePoint(): Either<StorageError, ResumePoint?> = point.right()

    override suspend fun store(point: ResumePoint): Either<StorageError, Unit> {
        this.point = point
        return Unit.right()
    }

    override suspend fun clear(): Either<StorageError, Unit> {
        point = null
        return Unit.right()
    }
}
