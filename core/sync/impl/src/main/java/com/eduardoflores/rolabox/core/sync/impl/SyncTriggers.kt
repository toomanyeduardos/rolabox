package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.auth.api.AuthState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Long enough that flicking through a setting's options syncs once, at the end. */
internal val LOCAL_CHANGE_DEBOUNCE = 2.seconds

/**
 * Emits whenever a sync should be requested: when a user signs in (including one already signed in
 * when the app starts), and once local changes settle for [debounce]. Coming to the foreground is
 * the third trigger, observed from the process lifecycle in the cloud flavor.
 */
@OptIn(FlowPreview::class)
internal fun syncRequests(
    authStates: Flow<AuthState>,
    localChanges: Flow<Unit>,
    debounce: Duration = LOCAL_CHANGE_DEBOUNCE,
): Flow<Unit> = merge(
    authStates
        .map { (it as? AuthState.SignedIn)?.user?.id }
        .distinctUntilChanged()
        .filterNotNull()
        .map { },
    localChanges.debounce(debounce),
)
