package com.eduardoflores.rolabox

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    private data object First : NavKey

    private data object Second : NavKey

    private fun navigator(flow: NavigationFlow) = AppNavigator(
        flowState = mutableStateOf(flow),
        authBackStack = NavBackStack(First),
        mainBackStack = NavBackStack(DeviceKey),
    )

    @Test
    fun startsOnTheGivenFlow() {
        assertEquals(NavigationFlow.Auth, navigator(NavigationFlow.Auth).flow)
        assertEquals(listOf<NavKey>(DeviceKey), navigator(NavigationFlow.Main).currentBackStack.toList())
    }

    @Test
    fun push_andPop_changeTheCurrentStack() {
        val navigator = navigator(NavigationFlow.Auth)

        navigator.push(Second)
        assertEquals(listOf<NavKey>(First, Second), navigator.currentBackStack.toList())

        navigator.pop()
        assertEquals(listOf<NavKey>(First), navigator.currentBackStack.toList())
    }

    @Test
    fun pop_keepsTheFirstScreen() {
        val navigator = navigator(NavigationFlow.Auth)

        navigator.pop()

        assertEquals(listOf<NavKey>(First), navigator.currentBackStack.toList())
    }

    @Test
    fun accessGranted_showsTheDeviceAndForgetsTheAuthScreens() {
        val navigator = navigator(NavigationFlow.Auth)
        navigator.push(Second)

        navigator.accessGranted()

        assertEquals(NavigationFlow.Main, navigator.flow)
        assertEquals(listOf<NavKey>(DeviceKey), navigator.currentBackStack.toList())
    }

    @Test
    fun accessGranted_meansBackNeverReturnsToAuth() {
        val navigator = navigator(NavigationFlow.Auth)
        navigator.push(Second)
        navigator.accessGranted()

        navigator.pop()

        assertEquals(NavigationFlow.Main, navigator.flow)
        assertEquals(listOf<NavKey>(DeviceKey), navigator.currentBackStack.toList())
    }

    // The generic exit (ADR-020): a full screen the device asked for goes on top of it, and back leaves it.
    @Test
    fun aFullScreenOpenedFromTheDevice_isPushedOverItAndPoppedBack() {
        val navigator = navigator(NavigationFlow.Main)

        navigator.push(Second)
        assertEquals(listOf<NavKey>(DeviceKey, Second), navigator.currentBackStack.toList())

        navigator.pop()
        assertEquals(listOf<NavKey>(DeviceKey), navigator.currentBackStack.toList())
    }

    @Test
    fun accessGranted_fromAScreenOverTheDevice_goesBackToTheDevice() {
        val navigator = navigator(NavigationFlow.Main)
        navigator.push(First)
        navigator.push(Second)

        navigator.accessGranted()

        assertEquals(NavigationFlow.Main, navigator.flow)
        assertEquals(listOf<NavKey>(DeviceKey), navigator.currentBackStack.toList())
    }
}
