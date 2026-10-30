package com.eduardoflores.rolabox.di

import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.impl.di.AuthModule
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import com.eduardoflores.rolabox.common.sync.impl.di.SyncModule
import com.eduardoflores.rolabox.common.sync.testing.FakeSyncRepository
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.userdata.impl.di.UserDataModule
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.Default
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.di.DispatchersModule
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.impl.di.LibraryModule
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.impl.di.PlaybackModule
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
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
// may depend on those (ADR-020). The fakes they bind live in each part's :testing module.
//
// AuthUseCasesModule isn't replaced: the real use cases, and what auth tells sync, run on the fake repositories.

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

// The device's tests check what the wheel asks of playback, and set what playback reports. The real player
// plays in real time, and is tested in :device:playback:impl, on the JVM and on a device (ADR-023).
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlaybackModule::class])
abstract class TestPlaybackModule {
    @Binds
    abstract fun bindsPlaybackState(fake: FakePlayback): PlaybackState

    @Binds
    abstract fun bindsPlaybackController(fake: FakePlayback): PlaybackController
}

// The debug build's library reads the files on the phone, so the tests bring their own.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LibraryModule::class])
object TestLibraryModule {
    @Provides
    @Singleton
    fun providesLibraryRepository(fake: FakeLibraryRepository): LibraryRepository = fake.apply {
        artists = TestLibrary.artists
        albums = TestLibrary.albums
        songs = TestLibrary.songs
    }
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
    abstract fun bindsSyncRepository(fake: FakeSyncRepository): SyncRepository
}
