package com.eduardoflores.rolabox

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.eduardoflores.rolabox.core.domain.StartDestination
import com.eduardoflores.rolabox.device.DeviceRoute

/** Shows what the navigation state points at (ADR-012). The entries of each part of the app are added here. */
@Composable
internal fun RolaboxNavigation(startDestination: StartDestination, modifier: Modifier = Modifier) {
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
            entry<DeviceKey> { DeviceRoute(Modifier.fillMaxSize()) }
            authEntries(navigator)
        },
    )
}
