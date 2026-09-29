package com.eduardoflores.rolabox.core.domain

import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveOfflineModeUseCaseTest {
    private val userDataRepository = FakeUserDataRepository()
    private val leaveOfflineMode = LeaveOfflineModeUseCase(userDataRepository)

    @Test
    fun turnsOfflineModeOff() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        val result = leaveOfflineMode()

        assertTrue(result.isRight())
        assertEquals(false, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun whenOfflineModeWasNotChosenItStaysOff() = runTest {
        assertTrue(leaveOfflineMode().isRight())

        assertEquals(false, userDataRepository.observeOfflineModeChosen().first().getOrNull())
    }

    @Test
    fun whenSavingFailsReturnsTheError() = runTest {
        userDataRepository.writeError = StorageError.Unavailable

        assertEquals(StorageError.Unavailable, leaveOfflineMode().leftOrNull())
    }
}
