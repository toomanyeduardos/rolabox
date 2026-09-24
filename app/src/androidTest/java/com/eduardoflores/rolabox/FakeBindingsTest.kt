package com.eduardoflores.rolabox

import arrow.core.right
import com.eduardoflores.rolabox.core.common.Dispatcher
import com.eduardoflores.rolabox.core.common.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.core.domain.repository.AuthRepository
import com.eduardoflores.rolabox.core.domain.repository.UserDataRepository
import com.eduardoflores.rolabox.core.model.AuthUser
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.sync.SyncManager
import com.eduardoflores.rolabox.core.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.testing.FakeSyncManager
import com.eduardoflores.rolabox.core.testing.FakeUserDataRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class FakeBindingsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var fakeAuthRepository: FakeAuthRepository

    @Inject lateinit var userDataRepository: UserDataRepository

    @Inject lateinit var fakeUserDataRepository: FakeUserDataRepository

    @Inject lateinit var syncManager: SyncManager

    @Inject lateinit var fakeSyncManager: FakeSyncManager

    @Inject lateinit var testDispatcher: TestDispatcher

    @Inject
    @Dispatcher(IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun productionBindings_areReplacedByFakes() {
        assertSame(fakeAuthRepository, authRepository)
        assertSame(fakeUserDataRepository, userDataRepository)
        assertSame(fakeSyncManager, syncManager)
        assertSame(testDispatcher, ioDispatcher)
    }

    @Test
    fun drivingAFake_isVisibleThroughTheInterface() = runTest {
        fakeAuthRepository.setUser(AuthUser(id = "1", displayName = "Ada"))
        userDataRepository.setDarkThemeConfig(DarkThemeConfig.DARK)

        assertEquals(AuthUser(id = "1", displayName = "Ada").right(), authRepository.observeCurrentUser().first())
        assertEquals(
            DarkThemeConfig.DARK.right(),
            fakeUserDataRepository.observeUserData().first().map { it.darkThemeConfig },
        )
    }
}
