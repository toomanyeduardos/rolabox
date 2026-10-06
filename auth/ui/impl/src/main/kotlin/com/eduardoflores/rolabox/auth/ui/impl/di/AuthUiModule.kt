package com.eduardoflores.rolabox.auth.ui.impl.di

import com.eduardoflores.rolabox.auth.ui.api.AuthUiEntries
import com.eduardoflores.rolabox.auth.ui.impl.DefaultAuthUiEntries
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the part's `:api`: its entry contract, which `:app` injects. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthUiModule {
    @Binds
    internal abstract fun bindsAuthUiEntries(entries: DefaultAuthUiEntries): AuthUiEntries
}
