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
import com.eduardoflores.rolabox.core.domain.StartDestination
import com.eduardoflores.rolabox.feature.account.SignInKey
import kotlinx.serialization.Serializable

/** The signed-in app's first screen: the device (ADR-018). */
@Serializable
internal data object DeviceKey : NavKey

/** The two parts of the app, each with a back stack of its own. */
internal enum class NavigationFlow { Auth, Main }

/**
 * The app's navigation state (ADR-012), and the only thing that changes it. Screens report their
 * exits, and `:app` turns each one into a call here.
 *
 * The auth flow is a stack of its own, outside the sections. Leaving it replaces it with the main
 * stack, so back never returns to it.
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

    /** Signed in or signed up: shows the main screens, and forgets the auth ones. */
    fun leaveAuth() {
        flowState.value = NavigationFlow.Main
        authBackStack.clear()
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
                StartDestination.Home -> NavigationFlow.Main
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
