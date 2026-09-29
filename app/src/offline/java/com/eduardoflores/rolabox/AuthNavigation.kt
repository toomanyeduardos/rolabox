package com.eduardoflores.rolabox

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

// This flavor has no accounts (ADR-008), so the app always starts on Home and the auth flow is never
// shown. Its stack still needs a first key, and Home is the one that can never be wrong.
internal fun authStartKey(): NavKey = HomeKey

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun EntryProviderScope<NavKey>.authEntries(navigator: AppNavigator) = Unit
