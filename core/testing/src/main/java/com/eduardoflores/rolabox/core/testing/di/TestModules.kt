package com.eduardoflores.rolabox.core.testing.di

import com.eduardoflores.rolabox.core.auth.AuthRepository
import com.eduardoflores.rolabox.core.auth.di.AuthModule
import com.eduardoflores.rolabox.core.common.Dispatcher
import com.eduardoflores.rolabox.core.common.RolaboxDispatchers.Default
import com.eduardoflores.rolabox.core.common.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.core.common.di.DispatchersModule
import com.eduardoflores.rolabox.core.data.di.DataModule
import com.eduardoflores.rolabox.core.data.repository.UserDataRepository
import com.eduardoflores.rolabox.core.sync.SyncManager
import com.eduardoflores.rolabox.core.sync.di.SyncModule
import com.eduardoflores.rolabox.core.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.testing.FakeSyncManager
import com.eduardoflores.rolabox.core.testing.FakeUserDataRepository
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
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
abstract class TestDataModule {
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
