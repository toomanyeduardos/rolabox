package com.eduardoflores.rolabox

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
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
    fun leaveAuth_showsTheDeviceAndForgetsTheAuthScreens() {
        val navigator = navigator(NavigationFlow.Auth)
        navigator.push(Second)

        navigator.leaveAuth()

        assertEquals(NavigationFlow.Main, navigator.flow)
        assertEquals(listOf<NavKey>(DeviceKey), navigator.currentBackStack.toList())
    }

    @Test
    fun leaveAuth_meansBackNeverReturnsToAuth() {
        val navigator = navigator(NavigationFlow.Auth)
        navigator.push(Second)
        navigator.leaveAuth()

        navigator.pop()

        assertEquals(NavigationFlow.Main, navigator.flow)
        assertEquals(listOf<NavKey>(DeviceKey), navigator.currentBackStack.toList())
    }
}
