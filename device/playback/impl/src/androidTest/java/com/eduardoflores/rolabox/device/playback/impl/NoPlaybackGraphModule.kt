package com.eduardoflores.rolabox.device.playback.impl

import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.playback.impl.di.PlaybackModule
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * This module's tests build [MediaPlayback] themselves, and the graph here has no library for it to read. The service
 * still reads Use Mobile Data for its player's stream (ADR-031, rule 9), so the settings are a fake's, On.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlaybackModule::class])
abstract class NoPlaybackGraphModule {
    @Binds
    abstract fun bindsUserDataRepository(fake: FakeUserDataRepository): UserDataRepository
}
