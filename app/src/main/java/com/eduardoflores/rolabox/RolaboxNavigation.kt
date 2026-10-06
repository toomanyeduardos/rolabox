package com.eduardoflores.rolabox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.eduardoflores.rolabox.auth.data.api.StartDestination
import com.eduardoflores.rolabox.auth.ui.api.AuthUiEntries
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries

/**
 * Shows what the app stack points at (ADR-012). The two areas add their own entries through their
 * entry contracts, and `:app` declares none (ADR-020).
 */
@Composable
internal fun RolaboxNavigation(
    startDestination: StartDestination,
    authUiEntries: AuthUiEntries,
    deviceUiEntries: DeviceUiEntries,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberAppNavigator(startDestination)
    NavDisplay(
        backStack = navigator.currentBackStack,
        modifier = modifier,
        onBack = navigator::pop,
        // A screen's saved state and ViewModels live as long as its entry is on the stack.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            authEntries(authUiEntries, navigator)
            deviceEntries(deviceUiEntries, navigator)
        },
    )
}
