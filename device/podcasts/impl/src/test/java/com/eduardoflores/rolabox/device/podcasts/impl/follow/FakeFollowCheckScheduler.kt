package com.eduardoflores.rolabox.device.podcasts.impl.follow

import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId

/** Records the checks requested, in [requested]: `null` for a check of every show, else the show's directory id. */
internal class FakeFollowCheckScheduler : FollowCheckScheduler {
    val requested = mutableListOf<DirectoryId?>()

    override fun checkAll() {
        requested += null
    }

    override fun checkShow(directoryId: DirectoryId) {
        requested += directoryId
    }
}
