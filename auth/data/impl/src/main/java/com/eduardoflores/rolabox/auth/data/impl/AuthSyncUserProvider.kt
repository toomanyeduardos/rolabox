package com.eduardoflores.rolabox.auth.data.impl

import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.common.sync.api.SyncUserProvider
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Tells sync who to sync for (ADR-020, rule 18): the signed-in user, or nobody while signed out or
 * while offline mode is chosen. Offline mode makes no Firebase requests (ADR-008 rule 6), and sync
 * obeys this before every trigger and every run.
 *
 * An offline-mode choice that can't be read counts as chosen, so a storage error never lets sync
 * reach Firebase.
 */
internal class AuthSyncUserProvider @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) : SyncUserProvider {
    override fun observeSyncUser(): Flow<String?> = combine(
        authRepository.observeAuthState(),
        userDataRepository.observeOfflineModeChosen().map { it.getOrNull() ?: true },
    ) { authState, offline ->
        (authState as? AuthState.SignedIn)?.user?.id?.takeUnless { offline }
    }.distinctUntilChanged()
}
