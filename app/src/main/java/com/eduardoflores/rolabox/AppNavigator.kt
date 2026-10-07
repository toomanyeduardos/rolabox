package com.eduardoflores.rolabox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.eduardoflores.rolabox.auth.data.api.StartDestination
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
import com.eduardoflores.rolabox.device.ui.api.DeviceKey

/** The two parts of the app, each with a back stack of its own. */
internal enum class NavigationFlow { Auth, Main }

/**
 * The app stack (ADR-012), and the only thing that changes it. Screens report their exits, and
 * `:app` turns each one into a call here. `:app` declares no key of its own (ADR-020): the stacks
 * start at keys of the two areas, which this is where they meet.
 *
 * The auth flow is a stack of its own. Leaving it replaces it with the main stack, which starts at
 * the device, so back never returns to it. Signing out does the same the other way around.
 */
internal class AppNavigator(
    private val flowState: MutableState<NavigationFlow>,
    private val authBackStack: NavBackStack<NavKey>,
    private val mainBackStack: NavBackStack<NavKey>,
) {
    val flow: NavigationFlow get() = flowState.value

    val currentBackStack: NavBackStack<NavKey>
        get() = when (flow) {
            NavigationFlow.Auth -> authBackStack
            NavigationFlow.Main -> mainBackStack
        }

    fun push(key: NavKey) {
        currentBackStack.add(key)
    }

    /** Goes back one screen. A stack's first screen stays: the system's back leaves the app from there. */
    fun pop() {
        if (currentBackStack.size > 1) currentBackStack.removeAt(currentBackStack.lastIndex)
    }

    /** Goes back to the stack's first screen. */
    fun popToRoot() {
        while (currentBackStack.size > 1) currentBackStack.removeAt(currentBackStack.lastIndex)
    }

    /**
     * Auth reports that access is granted: the user signed in or signed up, or chose offline mode.
     * From the auth flow, that shows the device and forgets the auth screens. From an auth screen
     * that was opened over the device, such as from Settings, it goes back to the device.
     */
    fun accessGranted() {
        when (flow) {
            NavigationFlow.Auth -> {
                flowState.value = NavigationFlow.Main
                authBackStack.clear()
            }

            NavigationFlow.Main -> popToRoot()
        }
    }

    /**
     * The user signed out from a screen over the device: that shows Sign in and forgets the device's
     * screens, so back never returns to the account that just left.
     */
    fun signedOut() {
        flowState.value = NavigationFlow.Auth
        authBackStack.clear()
        authBackStack.add(SignInKey)
        mainBackStack.clear()
        mainBackStack.add(DeviceKey)
    }
}

/**
 * The navigation state for a start destination. It's only used the first time: after that, `:app`
 * changes the state, and the saved one is restored (ADR-012).
 */
@Composable
internal fun rememberAppNavigator(startDestination: StartDestination): AppNavigator {
    val flowState = rememberSaveable {
        mutableStateOf(
            when (startDestination) {
                StartDestination.AccessGranted -> NavigationFlow.Main
                StartDestination.SignIn -> NavigationFlow.Auth
            },
        )
    }
    // Both stacks are always created, so that a flow can be entered later. The auth stack is emptied
    // when it's left, and doesn't come back.
    val authBackStack = rememberNavBackStack(SignInKey)
    val mainBackStack = rememberNavBackStack(DeviceKey)
    return AppNavigator(flowState, authBackStack, mainBackStack)
}
