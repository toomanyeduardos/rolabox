package com.eduardoflores.rolabox.di

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.impl.di.AuthModule
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.common.Dispatcher
import com.eduardoflores.rolabox.core.common.RolaboxDispatchers.Default
import com.eduardoflores.rolabox.core.common.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.core.common.di.DispatchersModule
import com.eduardoflores.rolabox.core.sync.api.SyncManager
import com.eduardoflores.rolabox.core.sync.impl.di.SyncModule
import com.eduardoflores.rolabox.core.sync.testing.FakeSyncManager
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.core.userdata.impl.di.UserDataModule
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

// These replace production Hilt modules, so they need the :impl modules, and :app is the only module that
// may depend on those (ADR-003). The fakes they bind live in each area's :testing module.

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatchersModule::class])
object TestDispatchersModule {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun providesTestDispatcher(): TestDispatcher = UnconfinedTestDispatcher()

    @Provides
    @Dispatcher(IO)
    fun providesIODispatcher(testDispatcher: TestDispatcher): CoroutineDispatcher = testDispatcher

    @Provides
    @Dispatcher(Default)
    fun providesDefaultDispatcher(testDispatcher: TestDispatcher): CoroutineDispatcher = testDispatcher
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [UserDataModule::class])
abstract class TestUserDataModule {
    @Binds
    abstract fun bindsUserDataRepository(fake: FakeUserDataRepository): UserDataRepository
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AuthModule::class])
abstract class TestAuthModule {
    @Binds
    abstract fun bindsAuthRepository(fake: FakeAuthRepository): AuthRepository
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SyncModule::class])
abstract class TestSyncModule {
    @Binds
    abstract fun bindsSyncManager(fake: FakeSyncManager): SyncManager
}
