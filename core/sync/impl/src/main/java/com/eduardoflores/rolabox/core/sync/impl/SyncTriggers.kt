package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.storage.api.StorageError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Long enough that flicking through a setting's options syncs once, at the end. */
internal val LOCAL_CHANGE_DEBOUNCE = 2.seconds

/**
 * The id of the user sync may run for, or null when it mustn't run: while signed out, or while
 * offline mode is chosen. Offline mode makes no Firebase requests (ADR-008 rule 6), and every sync
 * trigger and every sync run checks this first.
 *
 * An offline-mode choice that can't be read counts as chosen, so a storage error never lets sync
 * reach Firebase.
 */
internal fun syncUser(
    authStates: Flow<AuthState>,
    offlineModeChosen: Flow<Either<StorageError, Boolean>>,
): Flow<String?> = combine(authStates, offlineModeChosen.map { it.getOrNull() ?: true }) { authState, offline ->
    (authState as? AuthState.SignedIn)?.user?.id?.takeUnless { offline }
}.distinctUntilChanged()

/**
 * Emits whenever a sync should be requested, and only while [syncUser] allows it: once when it
 * starts allowing it (a user signs in, including one already signed in when the app starts), once
 * [localChanges] settle for [debounce], and whenever the app comes to the [foreground]. While sync
 * isn't allowed, none of these are even observed.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal fun syncRequests(
    syncUser: Flow<String?>,
    localChanges: Flow<Unit>,
    foreground: Flow<Unit>,
    debounce: Duration = LOCAL_CHANGE_DEBOUNCE,
): Flow<Unit> = syncUser.distinctUntilChanged().flatMapLatest { userId ->
    if (userId == null) emptyFlow() else merge(flowOf(Unit), localChanges.debounce(debounce), foreground)
}
