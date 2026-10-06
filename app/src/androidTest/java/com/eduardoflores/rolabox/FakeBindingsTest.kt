package com.eduardoflores.rolabox

import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import com.eduardoflores.rolabox.common.sync.testing.FakeSyncRepository
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
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

    @Inject lateinit var syncRepository: SyncRepository

    @Inject lateinit var fakeSyncRepository: FakeSyncRepository

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
        assertSame(fakeSyncRepository, syncRepository)
        assertSame(testDispatcher, ioDispatcher)
    }

    @Test
    fun drivingAFake_isVisibleThroughTheInterface() = runTest {
        val ada = AuthState.SignedIn(AuthUser(id = "1", displayName = "Ada", photoUrl = null))
        fakeAuthRepository.setAuthState(ada)
        userDataRepository.setDarkThemeConfig(DarkThemeConfig.DARK)

        assertEquals(ada, authRepository.observeAuthState().first())
        assertEquals(
            DarkThemeConfig.DARK.right(),
            fakeUserDataRepository.observeUserData().first().map { it.darkThemeConfig },
        )
    }
}
