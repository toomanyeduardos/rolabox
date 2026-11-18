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
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.Main
import com.eduardoflores.rolabox.common.util.di.DispatchersModule
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.impl.di.LibraryModule
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryRepository
import com.eduardoflores.rolabox.device.playback.api.EpisodeNetwork
import com.eduardoflores.rolabox.device.playback.api.ItemDownloads
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackState
import com.eduardoflores.rolabox.device.playback.impl.di.PlaybackModule
import com.eduardoflores.rolabox.device.playback.testing.FakeEpisodeNetwork
import com.eduardoflores.rolabox.device.playback.testing.FakeItemDownloads
import com.eduardoflores.rolabox.device.playback.testing.FakePlayback
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.impl.di.PodcastsModule
import com.eduardoflores.rolabox.device.podcasts.testing.FakeEpisodeProgressRepository
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
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

    @Provides
    @Dispatcher(Main)
    fun providesMainDispatcher(testDispatcher: TestDispatcher): CoroutineDispatcher = testDispatcher
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

    // The downloads and the network of episodes, which the podcasts' rows and Episode screen read (ADR-031).
    @Binds
    abstract fun bindsItemDownloads(fake: FakeItemDownloads): ItemDownloads

    @Binds
    abstract fun bindsEpisodeNetwork(fake: FakeEpisodeNetwork): EpisodeNetwork
}

// The real library reads the files on the phone, so the tests bring their own.
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

    // No song of the tests' library has a cover, so Now Playing shows the placeholder (ADR-026).
    @Provides
    fun providesArtworkRepository(fake: FakeArtworkRepository): ArtworkRepository = fake
}

// The real podcasts are a database on the phone and a directory on the internet, so the tests bring their own. Each
// test fills the fake with what it needs.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PodcastsModule::class])
abstract class TestPodcastsModule {
    @Binds
    abstract fun bindsPodcastsRepository(fake: FakePodcastsRepository): PodcastsRepository

    @Binds
    abstract fun bindsEpisodeProgressRepository(fake: FakeEpisodeProgressRepository): EpisodeProgressRepository

    @Binds
    abstract fun bindsPodcastDirectory(fake: FakePodcastDirectory): PodcastDirectory

    // No episode of the tests has artwork, so Now Playing shows the placeholder, and nothing is downloaded (ADR-031).
    @Binds
    abstract fun bindsPodcastArtworkRepository(fake: FakePodcastArtworkRepository): PodcastArtworkRepository
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
