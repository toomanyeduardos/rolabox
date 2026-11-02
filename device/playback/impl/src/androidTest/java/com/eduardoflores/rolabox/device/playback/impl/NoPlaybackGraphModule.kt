package com.eduardoflores.rolabox.device.playback.impl

import com.eduardoflores.rolabox.device.playback.impl.di.PlaybackModule
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** This module's tests build [MediaPlayback] themselves, and the graph here has no library for it to read. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlaybackModule::class])
object NoPlaybackGraphModule
