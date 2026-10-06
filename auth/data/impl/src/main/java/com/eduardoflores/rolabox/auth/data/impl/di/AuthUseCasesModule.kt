package com.eduardoflores.rolabox.auth.data.impl.di

import com.eduardoflores.rolabox.auth.data.api.PasswordPolicy
import com.eduardoflores.rolabox.auth.data.api.ResolveStartDestinationUseCase
import com.eduardoflores.rolabox.auth.data.api.SignInUseCase
import com.eduardoflores.rolabox.auth.data.api.SignUpUseCase
import com.eduardoflores.rolabox.auth.data.impl.AuthSyncUserProvider
import com.eduardoflores.rolabox.auth.data.impl.DefaultPasswordPolicy
import com.eduardoflores.rolabox.auth.data.impl.DefaultResolveStartDestinationUseCase
import com.eduardoflores.rolabox.auth.data.impl.DefaultSignInUseCase
import com.eduardoflores.rolabox.auth.data.impl.DefaultSignUpUseCase
import com.eduardoflores.rolabox.common.sync.api.SyncUserProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds auth's rules: the use cases built on the repositories (ADR-021), the password policy, and
 * what auth provides to sync (ADR-020, rule 18). It's apart from [AuthModule], so a test that
 * replaces the repository with a fake still gets the real rules on top of it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthUseCasesModule {
    @Binds
    internal abstract fun bindsSignInUseCase(useCase: DefaultSignInUseCase): SignInUseCase

    @Binds
    internal abstract fun bindsSignUpUseCase(useCase: DefaultSignUpUseCase): SignUpUseCase

    @Binds
    internal abstract fun bindsResolveStartDestinationUseCase(
        useCase: DefaultResolveStartDestinationUseCase,
    ): ResolveStartDestinationUseCase

    @Binds
    internal abstract fun bindsPasswordPolicy(policy: DefaultPasswordPolicy): PasswordPolicy

    @Binds
    internal abstract fun bindsSyncUserProvider(provider: AuthSyncUserProvider): SyncUserProvider
}
