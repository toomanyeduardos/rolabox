package com.eduardoflores.rolabox.common.sync.impl

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

/** Long enough that flicking through a setting's options syncs once, at the end. */
internal val LOCAL_CHANGE_DEBOUNCE = 2.seconds

/**
 * Emits whenever a sync should be requested, and only while there is a [syncUser] to sync for
 * (`SyncUserProvider`, which is where signed out and offline mode are decided: ADR-008 rule 6). It
 * emits once when there starts being one (a user signs in, including one already signed in when the
 * app starts), once [localChanges] settle for [debounce], and whenever the app comes to the
 * [foreground]. While there is nobody to sync for, none of these are even observed.
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
